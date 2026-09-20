import { useActivity, type Activity } from '../hooks/useActivity'
import { clock, compact } from '../lib/format'
import { ImpatienceMeter } from './ImpatienceMeter'
import { Spinner } from './Spinner'

interface Props {
  elapsedSeconds: number
  totals: { written: number; deleted: number; pastes: number }
  impatience: number
  /** Drives the word: typing, sitting there, or waiting on a submit. */
  activity: Activity
  running: boolean
  /** Report card is being generated — `^d` was pressed and confirmed. */
  finishing: boolean
  /** `^d` has been pressed once and is waiting for the confirming second. */
  armed: boolean
  /** They pressed `esc`. It is not their key to press. */
  escFlash: boolean
  /** How many of `SessionState.MAX_HINTS` are left this session. */
  hintsRemaining: number
  /** A hint call is in flight. */
  requestingHint: boolean
  onSubmit: () => void
  onEnd: () => void
  onHint: () => void
}

/**
 * The footer (UI-DESIGN.md §4.5), in the shape a coding agent puts it: a
 * spinner, what you are doing, and what it has cost so far in one parenthesis.
 *
 * <p>The clock is local, driven by a 1s interval in `App` — telemetry only
 * flushes when there are events, so a server-derived clock stalls the moment
 * you stop typing, which is exactly when the clock matters most.
 */
export function StatusLine({
  elapsedSeconds,
  totals,
  impatience,
  activity,
  running,
  finishing,
  armed,
  escFlash,
  hintsRemaining,
  requestingHint,
  onSubmit,
  onEnd,
  onHint,
}: Props) {
  const word = useActivity(activity, impatience)

  return (
    <div className="flex w-full flex-wrap items-center gap-x-5 gap-y-2 px-6 pt-3 pb-5 text-[13px] text-sub">
      {/* Not dimmable: this is the read on you, and §7 says that never recedes
          — least of all while you are typing, which is the state it reports. */}
      <span className="flex items-center gap-2">
        <Spinner />
        <span className="lowercase">{word}…</span>
        <span className="tabular-nums text-faint">
          <span className="sr-only">
            {clock(elapsedSeconds)} elapsed, {totals.written} characters written,{' '}
            {totals.deleted} deleted, {totals.pastes} pastes
          </span>
          <span aria-hidden>
            ({clock(elapsedSeconds)} · ↑{compact(totals.written)} ↓{compact(totals.deleted)} ⧉
            {totals.pastes})
          </span>
        </span>
      </span>

      <span className="dimmable">
        <ImpatienceMeter impatience={impatience} />
      </span>

      <span className="dimmable ml-auto flex items-center gap-5">
        <button
          type="button"
          onClick={onHint}
          disabled={requestingHint || hintsRemaining === 0 || running || finishing}
          className="lowercase transition-colors hover:text-ink disabled:opacity-40"
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ?
          </span>
          {requestingHint ? 'thinking…' : hintsRemaining === 0 ? 'no hints left' : `hint (${hintsRemaining} left)`}
        </button>
        <button
          type="button"
          onClick={onSubmit}
          disabled={running || finishing}
          className="lowercase transition-colors hover:text-ink disabled:opacity-40"
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ⏎
          </span>
          {running ? 'submitting…' : 'submit'}
        </button>
        <button
          type="button"
          onClick={onEnd}
          disabled={finishing}
          title={armed ? 'Click again to end the session' : 'Click once, then again to end'}
          className={`lowercase transition-colors hover:text-ink disabled:opacity-40 ${armed ? 'text-hot' : ''}`}
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ^d
          </span>
          {finishing ? 'grading…' : armed ? 'again to end' : 'end'}
        </button>
        {/* The tell. In a terminal this hint belongs to whoever is waiting on
            the model, and here that is not you. */}
        <span className={`lowercase ${escFlash ? 'text-hot' : 'text-faint'}`}>
          {escFlash ? 'esc is theirs' : 'esc to interrupt'}
        </span>
      </span>
    </div>
  )
}
