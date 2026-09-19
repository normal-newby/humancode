import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import { memo, useCallback, useEffect, useRef, useState } from 'react'
import type { TelemetryItem } from '../api/types'

interface Props {
  language: string
  initialCode: string
  onTelemetry: (item: TelemetryItem) => void
  onCodeChange: (code: string) => void
  onRun: () => void
}

/**
 * Monaco, dressed as a terminal composer and wired for surveillance
 * (UI-DESIGN.md §4.4).
 *
 * <p>Two signals matter. Content changes give inserted/deleted counts, which
 * feed the delete-ratio thrash detector. Paste events are captured separately
 * via `onDidPaste` — a large insert with no keystrokes behind it is a stronger
 * signal than any text classifier, and it is free.
 *
 * <p>The box border is the only border in the app: it is what makes the code
 * read as *your prompt*, the thing the machine is waiting on. Line numbers stay
 * on — the notes refer to them ("staring at line 12").
 */
function EditorPaneImpl({ language, initialCode, onTelemetry, onCodeChange, onRun }: Props) {
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const [focused, setFocused] = useState(false)
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
        // Must match --color-surface exactly, or the editor reads as an
        // embedded widget rather than as the composer box itself.
        'editor.background': '#24231f',
        'editorGutter.background': '#24231f',
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
      onCodeChange(editor.getValue())

      // Plain enter is a newline, obviously.
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current())

      editor.onDidChangeModelContent((event) => {
        let inserted = 0
        let deleted = 0
        for (const change of event.changes) {
          inserted += change.text.length
          deleted += change.rangeLength
        }
        if (inserted > 0 || deleted > 0) {
          onTelemetry({ type: 'EDIT', inserted, deleted })
        }
        onCodeChange(editor.getValue())
      })

      editor.onDidPaste((event) => {
        const pasted = editor.getModel()?.getValueInRange(event.range) ?? ''
        onTelemetry({
          type: 'PASTE',
          inserted: pasted.length,
          deleted: 0,
          detail: `${pasted.length} chars`,
        })
      })

      editor.onDidFocusEditorText(() => {
        setFocused(true)
        onTelemetry({ type: 'FOCUS', inserted: 0, deleted: 0 })
      })
      editor.onDidBlurEditorText(() => {
        setFocused(false)
        onTelemetry({ type: 'BLUR', inserted: 0, deleted: 0 })
      })
    },
    [onCodeChange, onTelemetry],
  )

  return (
    <div className="mx-auto w-full max-w-[84ch] px-6">
      <div
        className={`relative h-[42vh] overflow-hidden rounded-md border bg-surface transition-colors duration-200 ${
          focused ? 'border-accent' : 'border-faint'
        }`}
      >
        {/* The prompt mark. Sits beside line 1 rather than replacing its
            number, which the notes need to stay referable. */}
        <span
          aria-hidden
          className="pointer-events-none absolute top-2 left-2 z-10 text-sm leading-[1.7] text-faint select-none"
        >
          &gt;
        </span>

        {/* Padded clear of the `>` so the mark never sits on the gutter. */}
        <div className="h-full pl-6">
          <Editor
            height="100%"
            defaultLanguage={language}
            defaultValue={initialCode}
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
              lineDecorationsWidth: 12,
              lineNumbersMinChars: 3,
              padding: { top: 8, bottom: 24 },
              tabSize: 2,
              automaticLayout: true,
              cursorBlinking: 'smooth',
              smoothScrolling: true,
              scrollbar: { vertical: 'auto', horizontal: 'auto', useShadows: false },
            }}
          />
        </div>
      </div>
    </div>
  )
}

/** App re-renders on every keystroke (the counters live there); this must not. */
export const EditorPane = memo(EditorPaneImpl)
