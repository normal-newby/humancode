import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import { useCallback, useRef } from 'react'
import type { TelemetryItem } from '../api/types'

interface Props {
  language: string
  initialCode: string
  onTelemetry: (item: TelemetryItem) => void
  onCodeChange: (code: string) => void
}

/**
 * Monaco, wired for surveillance and stripped of chrome (UI-DESIGN.md §4.2).
 *
 * <p>Two signals matter. Content changes give inserted/deleted counts, which
 * feed the delete-ratio thrash detector. Paste events are captured separately
 * via `onDidPaste` — a large insert with no keystrokes behind it is a stronger
 * signal than any text classifier, and it is free.
 *
 * <p>The theme matches the page background exactly so the editor reads as the
 * page rather than as an embedded widget. Line numbers stay on: the interviewer
 * refers to them in its notes ("staring at line 12").
 */
export function EditorPane({ language, initialCode, onTelemetry, onCodeChange }: Props) {
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)

  const handleBeforeMount = useCallback<BeforeMount>((monaco) => {
    monaco.editor.defineTheme('humancode', {
      base: 'vs-dark',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#232427',
        'editorGutter.background': '#232427',
        'editor.lineHighlightBackground': '#00000000',
        'editor.lineHighlightBorder': '#00000000',
        'editorLineNumber.foreground': '#45474b',
        'editorLineNumber.activeForeground': '#6b6d72',
        'editorCursor.foreground': '#8b9fff',
        'editorIndentGuide.background1': '#2a2b2f',
        'editorIndentGuide.activeBackground1': '#45474b',
        'editorWidget.background': '#2a2b2f',
        'scrollbarSlider.background': '#2a2b2f',
        'scrollbarSlider.hoverBackground': '#45474b',
        'scrollbarSlider.activeBackground': '#45474b',
      },
    })
  }, [])

  const handleMount = useCallback<OnMount>(
    (editor) => {
      editorRef.current = editor
      onCodeChange(editor.getValue())

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

      editor.onDidFocusEditorText(() => onTelemetry({ type: 'FOCUS', inserted: 0, deleted: 0 }))
      editor.onDidBlurEditorText(() => onTelemetry({ type: 'BLUR', inserted: 0, deleted: 0 }))
    },
    [onCodeChange, onTelemetry],
  )

  return (
    <div className="mx-auto min-h-0 w-full max-w-[78ch] flex-1">
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
          padding: { top: 8, bottom: 32 },
          tabSize: 2,
          automaticLayout: true,
          cursorBlinking: 'smooth',
          smoothScrolling: true,
          scrollbar: { vertical: 'auto', horizontal: 'auto', useShadows: false },
        }}
      />
    </div>
  )
}
