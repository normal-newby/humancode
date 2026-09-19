import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { NotePayload, Problem } from '../api/types'
import { MetaLine, type TurnStamp } from './MetaLine'
import { NotesBlock } from './NotesBlock'
import { TypedText } from './TypedText'

/** One line in the log: something the interviewer said, and its receipt. */
export interface Entry {
  id: string
  line: string
  canned: boolean
  stamp: TurnStamp | null
}

interface Props {
  problem: Problem
  entries: Entry[]
  /** The pinned statement's stamp: session totals, ticking (UI-DESIGN.md §5). */
  liveStamp: TurnStamp
  notes: NotePayload[]
  busy: boolean
  busyLabel?: string
  connected: boolean
}

/** A turn: the `⏺` marker, then the line and its meta line, hanging-indented. */
function Turn({ accent, children }: { accent?: boolean; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[1.25rem_1fr] gap-x-2">
      <span aria-hidden className={accent ? 'text-accent' : 'text-faint'}>
        ⏺
      </span>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

/**
 * One spoken turn. Types itself out if it arrived while mounted; renders whole
 * if it was already on screen. The meta line waits for the line to finish — the
 * receipt lands after the sentence, never during it.
 */
function TurnEntry({
  entry,
  newest,
  onTick,
}: {
  entry: Entry
  newest: boolean
  onTick: () => void
}) {
  /** Captured at mount: a turn only ever types on the way in. */
  const [animate] = useState(newest)
  const [done, setDone] = useState(!animate)
  const handleDone = useCallback(() => setDone(true), [])

  return (
    <div className={newest ? 'animate-turn-in' : 'dimmable'}>
      <Turn accent={newest}>
        <TypedText
          text={entry.line}
          animate={animate}
          onTick={onTick}
          onDone={handleDone}
          className={`text-[15px] leading-relaxed transition-colors duration-[400ms] ${
            newest ? 'text-ink' : 'text-faint'
          }`}
        />
        {done && entry.stamp && <MetaLine stamp={entry.stamp} canned={entry.canned} />}
      </Turn>
    </div>
  )
}

/**
 * The interviewer's log (UI-DESIGN.md §4.2).
 *
 * <p>It keeps its scrollback, unlike the single fading utterance this replaced:
 * a meta line is a log stamp and only means anything if the thing it stamps
 * stays on screen. Past turns dim to `--color-faint` so the newest line is
 * still unmistakably the live one.
 *
 * <p>Test verdicts never appear here. The runner's result goes to the server as
 * a gauge of progress and the interviewer decides what, if anything, to say
 * about it — see UI-DESIGN.md §4.7.
 */
export function Transcript({
  problem,
  entries,
  liveStamp,
  notes,
  busy,
  busyLabel,
  connected,
}: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const endRef = useRef<HTMLDivElement>(null)
  /** Auto-scroll only while the candidate is already at the bottom. */
  const stuck = useRef(true)
  const [statementDone, setStatementDone] = useState(false)
  const handleStatementDone = useCallback(() => setStatementDone(true), [])

  const follow = useCallback((smooth = false) => {
    if (!stuck.current) return
    endRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'end' })
  }, [])

  useEffect(() => {
    follow(true)
  }, [entries.length, notes.length, busy, follow])

  const onScroll = () => {
    const element = scrollRef.current
    if (!element) return
    stuck.current = element.scrollHeight - element.scrollTop - element.clientHeight < 48
  }

  const lastTurnId = entries.length ? entries[entries.length - 1].id : null

  return (
    <div ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-[84ch] px-6">
        {/* The problem is the first turn and it does not go away. */}
        <div className="dimmable-soft sticky top-0 z-10 bg-canvas pt-8 pb-3 transition-opacity duration-300">
          <Turn>
            <TypedText
              text={problem.statement}
              animate
              onDone={handleStatementDone}
              className="text-[15px] leading-relaxed text-ink"
            />
            {/* No worked examples here. An `in … out …` pair is a test case with
                better manners — see UI-DESIGN.md §4.7. */}
            {statementDone && <MetaLine stamp={liveStamp} live />}
          </Turn>
          <div
            aria-hidden
            className="mt-3 overflow-hidden text-xs whitespace-nowrap text-faint select-none"
          >
            ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
          </div>
        </div>

        <div className="space-y-5 pt-5 pb-6" aria-live="polite">
          {entries.length === 0 && !busy && (
            <p className="text-xs text-faint">{connected ? 'they are watching' : 'connecting…'}</p>
          )}

          {entries.map((entry) => (
            <TurnEntry
              key={entry.id}
              entry={entry}
              newest={entry.id === lastTurnId}
              onTick={follow}
            />
          ))}

          <NotesBlock notes={notes} busy={busy} busyLabel={busyLabel} />
          <div ref={endRef} />
        </div>
      </div>
    </div>
  )
}
