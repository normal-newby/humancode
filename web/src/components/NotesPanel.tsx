import { useEffect, useRef } from 'react'
import type { NotePayload } from '../api/types'

/**
 * The interviewer's "private" notes, which you can of course read
 * (UI-DESIGN.md §4.4).
 *
 * <p>Margin annotations, not chat messages: no bubbles, no timestamps, no
 * border box. Deliberately NOT an aria-live region — these would interrupt a
 * screen reader constantly, and the joke depends on them being peripheral.
 */
export function NotesPanel({ notes }: { notes: NotePayload[] }) {
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [notes.length])

  return (
    <aside className="dimmable flex min-h-0 flex-col">
      <h2 className="shrink-0 text-xs lowercase text-sub">
        private notes <span className="text-faint">(do not read)</span>
      </h2>

      <div className="mt-4 min-h-0 flex-1 space-y-3 overflow-y-auto">
        {notes.length === 0 && <p className="text-[11px] text-faint">nothing yet</p>}

        {notes.map((note, index) => (
          <p
            key={`${note.at}-${index}`}
            className="animate-note-in border-l-2 border-faint pl-3 text-xs leading-relaxed lowercase text-sub"
          >
            {note.note}
          </p>
        ))}
        <div ref={endRef} />
      </div>
    </aside>
  )
}
