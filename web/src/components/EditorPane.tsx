import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import { memo, useCallback, useEffect, useRef, useState } from 'react'
import type { ProblemFile, TelemetryItem } from '../api/types'

type Monaco = Parameters<OnMount>[1]
type TextModel = ReturnType<Monaco['editor']['createModel']>

interface Props {
  files: ProblemFile[]
  onTelemetry: (item: TelemetryItem) => void
  onCodeChange: (file: string, code: string) => void
  onRun: () => void
}

/**
 * Monaco, dressed as the block you are currently emitting, and wired for
 * surveillance (UI-DESIGN.md §4.3) — now across however many files the
 * problem actually needs.
 *
 * <p>One editor instance, one model per file (Monaco's standard multi-model
 * pattern), swapped via `setModel` rather than mounting N editors — this is
 * what preserves each file's undo history and cursor position across
 * switches. The change listener is registered once on the editor, not per
 * model: Monaco delegates it to whichever model is currently attached, so it
 * keeps firing correctly across switches. `activeFileRef` is kept in sync on
 * every switch so the listener can tag each event with the right file.
 *
 * <p>Two signals matter. Content changes give inserted/deleted counts, which
 * feed the delete-ratio thrash detector. Paste events are captured separately
 * via `onDidPaste` — a large insert with no keystrokes behind it is a stronger
 * signal than any text classifier, and it is free.
 *
 * <p>No border and no fill: it is output, not an input box, and it has to read
 * as part of the page. Line numbers stay on — their notes refer to them
 * ("staring at line 12").
 */
function EditorPaneImpl({ files, onTelemetry, onCodeChange, onRun }: Props) {
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const modelsRef = useRef<Map<string, TextModel>>(new Map())
  const [activeFile, setActiveFile] = useState(files[0]?.name ?? '')
  const activeFileRef = useRef(activeFile)

  /** Kept in a ref so ctrl+enter never rebinds against a stale closure. */
  const runRef = useRef(onRun)
  useEffect(() => {
    runRef.current = onRun
  }, [onRun])

  const handleBeforeMount = useCallback<BeforeMount>((monaco) => {
    monaco.editor.defineTheme('humancode', {
      base: 'vs-dark',
      inherit: true,
      rules: [],
      colors: {
        // Must match --color-canvas exactly, or the turn reads as an embedded
        // widget rather than as text on the page.
        'editor.background': '#1c1b19',
        'editorGutter.background': '#1c1b19',
        'editor.lineHighlightBackground': '#00000000',
        'editor.lineHighlightBorder': '#00000000',
        'editorLineNumber.foreground': '#4b4841',
        'editorLineNumber.activeForeground': '#8a857a',
        'editorCursor.foreground': '#d98b63',
        'editorIndentGuide.background1': '#2d2c27',
        'editorIndentGuide.activeBackground1': '#4b4841',
        'editorWidget.background': '#24231f',
        'scrollbarSlider.background': '#2d2c27',
        'scrollbarSlider.hoverBackground': '#4b4841',
        'scrollbarSlider.activeBackground': '#4b4841',
      },
    })
  }, [])

  const handleMount = useCallback<OnMount>(
    (editor, monaco) => {
      editorRef.current = editor

      for (const file of files) {
        const model = monaco.editor.createModel(file.starterContent, file.language)
        modelsRef.current.set(file.name, model)
        onCodeChange(file.name, file.starterContent)
      }
      const first = modelsRef.current.get(activeFileRef.current)
      if (first) {
        editor.setModel(first)
      }

      // Plain enter is a newline, obviously.
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current())

      editor.onDidChangeModelContent((event) => {
        let inserted = 0
        let deleted = 0
        for (const change of event.changes) {
          inserted += change.text.length
          deleted += change.rangeLength
        }
        const file = activeFileRef.current
        if (inserted > 0 || deleted > 0) {
          onTelemetry({ type: 'EDIT', inserted, deleted, file })
        }
        onCodeChange(file, editor.getValue())
      })

      editor.onDidPaste((event) => {
        const pasted = editor.getModel()?.getValueInRange(event.range) ?? ''
        onTelemetry({
          type: 'PASTE',
          inserted: pasted.length,
          deleted: 0,
          file: activeFileRef.current,
          detail: `${pasted.length} chars`,
        })
      })

      editor.onDidFocusEditorText(() =>
        onTelemetry({ type: 'FOCUS', inserted: 0, deleted: 0, file: activeFileRef.current }),
      )
      editor.onDidBlurEditorText(() =>
        onTelemetry({ type: 'BLUR', inserted: 0, deleted: 0, file: activeFileRef.current }),
      )
    },
    // files is fixed for the life of a session — read once at mount, same as
    // the old defaultValue/defaultLanguage props were.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [onCodeChange, onTelemetry],
  )

  const switchFile = useCallback((file: string) => {
    activeFileRef.current = file
    setActiveFile(file)
    const model = modelsRef.current.get(file)
    if (model && editorRef.current) {
      editorRef.current.setModel(model)
    }
  }, [])

  useEffect(
    () => () => {
      for (const model of modelsRef.current.values()) {
        model.dispose()
      }
    },
    [],
  )

  return (
    <div className="flex h-full flex-col">
      {files.length > 1 && (
        <div className="mb-1 flex items-baseline gap-4 text-[13px]">
          {files.map((file) => (
            <button
              key={file.name}
              type="button"
              onClick={() => switchFile(file.name)}
              className={`lowercase transition-colors ${
                file.name === activeFile ? 'text-accent' : 'text-faint hover:text-sub'
              }`}
            >
              {file.name}
            </button>
          ))}
        </div>
      )}
      <div className="min-h-0 flex-1">
        <Editor
          height="100%"
          beforeMount={handleBeforeMount}
          onMount={handleMount}
          theme="humancode"
          options={{
            fontSize: 14,
            lineHeight: 1.7,
            fontFamily: '"JetBrains Mono", "Roboto Mono", ui-monospace, monospace',
            fontLigatures: false,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            renderLineHighlight: 'none',
            overviewRulerLanes: 0,
            hideCursorInOverviewRuler: true,
            overviewRulerBorder: false,
            folding: false,
            glyphMargin: false,
            lineDecorationsWidth: 8,
            lineNumbersMinChars: 3,
            padding: { top: 4, bottom: 16 },
            tabSize: 2,
            automaticLayout: true,
            cursorBlinking: 'smooth',
            smoothScrolling: true,
            scrollbar: { vertical: 'auto', horizontal: 'auto', useShadows: false },
          }}
        />
      </div>
    </div>
  )
}

/** App re-renders on every keystroke (the counters live there); this must not. */
export const EditorPane = memo(EditorPaneImpl)
