import { useEffect, useState } from 'react'
import type { NotePayload } from '../api/types'
import { Spinner } from './Spinner'

interface Props {
  notes: NotePayload[]
  /** Something is in flight — the spinner variant of the same line. */
  busy: boolean
  busyLabel?: string
}

/**
 * The interviewer's "private" notes (UI-DESIGN.md §4.3).
 *
 * <p>Collapsed by default and it never opens itself: the candidate choosing to
 * peek, and finding out what it thinks of them, is the beat. Deliberately NOT
 * an aria-live region — these would interrupt a screen reader constantly, and
 * the joke depends on them being peripheral.
 */
export function NotesBlock({ notes, busy, busyLabel = 'thinking…' }: Props) {
  const [open, setOpen] = useState(false)

  // ctrl+o, the way a terminal agent expands its own thinking.
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.ctrlKey && !event.altKey && !event.metaKey && event.key.toLowerCase() === 'o') {
        event.preventDefault()
        setOpen((previous) => !previous)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  if (busy) {
    return (
      <div className="dimmable mt-5 flex items-baseline gap-2 text-xs text-sub">
        <Spinner />
        <span>{busyLabel}</span>
      </div>
    )
  }

  if (notes.length === 0) return null

  return (
    <div className="dimmable mt-5">
      <button
        type="button"
        onClick={() => setOpen((previous) => !previous)}
        aria-expanded={open}
        className="flex items-baseline gap-2 text-xs lowercase text-sub transition-colors hover:text-ink"
      >
        <span aria-hidden className="text-faint">
          ✻
        </span>
        <span>
          taking notes… <span className="tabular-nums">({notes.length})</span>
        </span>
        <span className="text-[10px] text-faint">{open ? 'ctrl+o to hide' : 'ctrl+o'}</span>
      </button>

      {open && (
        <div className="mt-2 space-y-1">
          {notes.map((note, index) => (
            <p
              key={`${note.at}-${index}`}
              className="animate-note-in flex items-baseline gap-2 text-xs leading-relaxed lowercase text-sub"
            >
              <span aria-hidden className="text-faint">
                ⎿
              </span>
              <span>{note.note}</span>
            </p>
          ))}
        </div>
      )}
    </div>
  )
}
