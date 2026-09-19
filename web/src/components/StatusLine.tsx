import { useActivity, type Activity } from '../hooks/useActivity'
import { clock, compact } from '../lib/format'
import { ImpatienceMeter } from './ImpatienceMeter'
import { Spinner } from './Spinner'

interface Props {
  elapsedSeconds: number
  totals: { written: number; deleted: number; pastes: number }
  impatience: number
  /** Drives the word: typing, sitting there, or waiting on a run. */
  activity: Activity
  running: boolean
  /** Escape has been pressed once and is waiting for the confirming second. */
  armed: boolean
  onRun: () => void
  onEnd: () => void
}

/**
 * Everything that used to be a rail, on one line under the composer
 * (UI-DESIGN.md §4.5). Session totals use the same glyphs as the meta lines so
 * the eye connects them; the per-turn numbers up there are deltas of these.
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
  armed,
  onRun,
  onEnd,
}: Props) {
  const word = useActivity(activity, impatience)

  return (
    <div className="mx-auto flex w-full max-w-[84ch] flex-wrap items-center gap-x-7 gap-y-2 px-6 pt-4 pb-5 text-[13px] text-sub">
      {/* Not dimmable: this is the interviewer's read on you, and §7 says the
          interviewer never recedes — least of all while you are typing, which
          is the state it is reporting. */}
      <span className="flex items-center gap-2">
        <Spinner />
        <span className="lowercase">{word}…</span>
      </span>

      <span className="dimmable flex flex-wrap items-center gap-x-7 gap-y-2">
        <span className="tabular-nums">
          <span className="sr-only">elapsed </span>
          {clock(elapsedSeconds)}
        </span>

        <span className="tabular-nums">
          <span className="sr-only">
            {totals.written} characters written, {totals.deleted} deleted, {totals.pastes} pastes
          </span>
          <span aria-hidden>
            ↑{compact(totals.written)} ↓{compact(totals.deleted)} ⧉{totals.pastes}
          </span>
        </span>

        <ImpatienceMeter impatience={impatience} />
      </span>

      <span className="dimmable ml-auto flex items-center gap-5">
        <button
          type="button"
          onClick={onRun}
          disabled={running}
          className="lowercase transition-colors hover:text-ink disabled:opacity-40"
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ⏎
          </span>
          {running ? 'running…' : 'run'}
        </button>
        <button
          type="button"
          onClick={onEnd}
          className={`lowercase transition-colors hover:text-ink ${armed ? 'text-hot' : ''}`}
        >
          <span aria-hidden className="mr-1.5 text-faint">
            esc
          </span>
          {armed ? 'again to end' : 'end'}
        </button>
      </span>
    </div>
  )
}
