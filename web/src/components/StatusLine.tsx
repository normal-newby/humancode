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
  /** Report card is being generated — `^d` was pressed and confirmed. */
  finishing: boolean
  /** `^d` has been pressed once and is waiting for the confirming second. */
  armed: boolean
  /** How many of `SessionState.MAX_HINTS` are left this session. */
  hintsRemaining: number
  /** A hint call is in flight. */
  requestingHint: boolean
  /** The interviewer's voice is off. */
  muted: boolean
  onToggleMuted: () => void
  /**
   * Hands the work in, which also ends the session — there is only one of
   * these now. It is armed on the first press and acts on the second, because
   * it is the last thing you do.
   */
  onSubmit: () => void
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
  finishing,
  armed,
  hintsRemaining,
  requestingHint,
  muted,
  onToggleMuted,
  onSubmit,
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
          disabled={requestingHint || hintsRemaining === 0 || finishing}
          className="lowercase transition-colors hover:text-ink disabled:opacity-40"
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ?
          </span>
          {requestingHint ? 'thinking…' : hintsRemaining === 0 ? 'no hints left' : `hint (${hintsRemaining} left)`}
        </button>
        {/* Words, not a speaker icon: §2 bans icons in the chrome and §4.1
            fixes the glyph set, so the voice gets the same `^x verb` shape the
            keys beside it already use. */}
        <button
          type="button"
          onClick={onToggleMuted}
          title={muted ? 'Let them speak' : 'Silence them'}
          className="lowercase transition-colors hover:text-ink"
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ^m
          </span>
          {muted ? 'muted' : 'mute'}
        </button>
        {/* The one terminal action. Handing the work in *is* the end of the
            session now, so there is no separate `end` beside it and nothing
            that closes a turn without closing the interview. Still two
            presses: it is irreversible, and `^d` is EOF, which is the right
            verb for it. */}
        <button
          type="button"
          onClick={onSubmit}
          disabled={finishing}
          title={armed ? 'Click again to submit' : 'Click once, then again to submit'}
          className={`lowercase transition-colors hover:text-ink disabled:opacity-40 ${armed ? 'text-hot' : ''}`}
        >
          <span aria-hidden className="mr-1.5 text-faint">
            ^d
          </span>
          {finishing ? 'grading…' : armed ? 'again to submit' : 'submit'}
        </button>
      </span>
    </div>
  )
}
