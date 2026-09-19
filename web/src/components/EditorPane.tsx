import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import { memo, useCallback, useEffect, useRef, useState } from 'react'
import type { ProblemFile, TelemetryItem } from '../api/types'
import { buildPreviewDocument, hasPreview, type FileContents } from '../lib/buildPreview'
import { PreviewPane } from './PreviewPane'

/** The navigator entry that is not a file. */
const PREVIEW_VIEW = 'preview'

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

  /**
   * Which navigator entry is showing: a filename, or `preview`.
   *
   * <p>Switching is a view change, not a new turn (§4.3a) — it does not touch
   * the meta line, the turn boundary or the diff baseline.
   */
  const [view, setView] = useState<string>(files[0]?.name ?? '')
  const showingPreview = view === PREVIEW_VIEW
  const previewable = hasPreview(files)

  /**
   * Latest content per file, read from the models rather than lifted into
   * App: the preview is the only thing that wants it, and putting it in App's
   * state would re-render the whole log on every keystroke.
   */
  const [previewDoc, setPreviewDoc] = useState<string | null>(null)

  const rebuildPreview = useCallback(() => {
    const contents: FileContents = {}
    for (const [name, model] of modelsRef.current) {
      contents[name] = model.getValue()
    }
    setPreviewDoc(buildPreviewDocument(files, contents))
    // files is fixed for the life of a session — see handleMount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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

  const switchTo = useCallback(
    (next: string) => {
      setView(next)
      if (next === PREVIEW_VIEW) {
        // Rebuilt here and only here. The editor and the preview share one
        // slot, so the buffers cannot change while the page is the thing on
        // screen — there is no such thing as a stale preview to debounce, and
        // a timer watching for one would never fire. What the candidate gets
        // is the same guarantee either way: what you switch to is current.
        rebuildPreview()
        return
      }
      activeFileRef.current = next
      setActiveFile(next)
      const model = modelsRef.current.get(next)
      if (model && editorRef.current) {
        editorRef.current.setModel(model)
      }
    },
    [rebuildPreview],
  )

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
      {(files.length > 1 || previewable) && (
        <div className="mb-1 flex items-baseline gap-4 text-[13px]">
          {files.map((file) => (
            <button
              key={file.name}
              type="button"
              onClick={() => switchTo(file.name)}
              className={`lowercase transition-colors ${
                file.name === activeFile && !showingPreview
                  ? 'text-accent'
                  : 'text-faint hover:text-sub'
              }`}
            >
              {file.name}
            </button>
          ))}
          {previewable && (
            // Set apart by a wider gap: it is in the same row because it is
            // another way to look at your own output, but it is not a file.
            <button
              type="button"
              onClick={() => switchTo(PREVIEW_VIEW)}
              className={`ml-4 lowercase transition-colors ${
                showingPreview ? 'text-accent' : 'text-faint hover:text-sub'
              }`}
            >
              {PREVIEW_VIEW}
            </button>
          )}
        </div>
      )}
      {/*
        Both live in the same slot, and the editor is hidden rather than
        unmounted: dropping it would throw away every model's undo history and
        cursor position, which switching files is specifically built to keep.
      */}
      <div className={`min-h-0 flex-1 ${showingPreview ? 'hidden' : ''}`}>
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
      {showingPreview && (
        <div className="min-h-0 flex-1">
          <PreviewPane document={previewDoc} />
        </div>
      )}
    </div>
  )
}

/** App re-renders on every keystroke (the counters live there); this must not. */
export const EditorPane = memo(EditorPaneImpl)
