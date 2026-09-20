import { useEffect, useState } from 'react'
import { fetchLeaderboard } from '../api/client'
import type { LeaderboardResponse, UserProfile } from '../api/types'
import { BOOT_COMMAND } from './BootSequence'
import { Spinner } from './Spinner'
import { Block, Result } from './Transcript'
import { WindowTab } from './WindowTab'

interface Props {
  /** The viewer, so the server can mark their row and report their standing. */
  profile: UserProfile | null
  /** Threaded to the tab, which shows the same number on every screen. */
  rating: number
  handle?: string
  onBack: () => void
  onBegin: () => void
}

function sign(rating: number): string {
  return rating > 0 ? '+' : ''
}

function tone(rating: number): string {
  return rating > 0 ? 'text-calm' : rating < 0 ? 'text-hot' : 'text-sub'
}

/**
 * The board.
 *
 * <p>The fifth screen under the one window tab, and it obeys the same rule the
 * other four do (UI-DESIGN.md §4.0): it is not a page that replaced the app, it
 * is the same terminal printing something else. So it is a `•` block with `└`
 * receipts and a plain row of aligned numbers — no table chrome, no borders, no
 * medals, no podium. §2's whole point is that furniture is what turns this back
 * into a practice site, and a leaderboard is exactly where trophies would show
 * up uninvited.
 *
 * <p>The one accent on screen is the viewer's own handle, which follows §4.1's
 * rule as closely as a board can: accent marks whoever the screen is about.
 */
export function Leaderboard({ profile, rating, handle, onBack, onBegin }: Props) {
  const [board, setBoard] = useState<LeaderboardResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let live = true
    fetchLeaderboard(handle)
      .then((result) => live && setBoard(result))
      .catch((e: unknown) => live && setError(e instanceof Error ? e.message : String(e)))
    return () => {
      live = false
    }
  }, [handle])

  const you = board?.you ?? profile
  // Only worth printing when the board did not already reach them — otherwise
  // it is their own row repeated two lines below itself.
  const listed = board?.entries.some((entry) => entry.you) ?? false

  return (
    <main className="flex min-h-screen flex-col bg-canvas">
      <WindowTab status="leaderboard" rating={rating} handle={handle} />

      <div className="mx-auto w-full max-w-[84ch] px-6 pt-12 pb-16">
        <Block marker="•" tone="text-faint">
          <p className="text-[15px] leading-relaxed text-sub">the board</p>
          {board && (
            <Result>
              {board.total === 0
                ? 'nobody has been judged yet'
                : `top ${board.entries.length} of ${board.total} judged`}
            </Result>
          )}
          {!board && !error && (
            <Result tone="text-faint">
              <Spinner /> reading the board…
            </Result>
          )}
          {error && (
            <Result tone="text-hot">could not read the board. {error}</Result>
          )}
        </Block>

        {/* The list is narrower than the 84ch column around it on purpose: at
            full width the `1fr` handle column shoves the rating to the far
            edge and a row stops reading as one fact about one person. */}
        {board && board.entries.length > 0 && (
          <ol className="mt-8 max-w-[52ch] space-y-2">
            {board.entries.map((entry) => (
              <li
                key={entry.handle}
                className="grid grid-cols-[3ch_1fr_auto] items-baseline gap-x-4 text-sm lowercase tabular-nums"
              >
                <span className="text-right text-faint">{entry.rank}</span>
                <span className={entry.you ? 'truncate text-accent' : 'truncate text-sub'}>
                  {entry.handle}
                  {entry.you && <span className="sr-only"> (you)</span>}
                </span>
                <span className="flex items-baseline gap-4">
                  <span className={tone(entry.rating)}>
                    {sign(entry.rating)}
                    {entry.rating}
                  </span>
                  {/* The session count is context, not a score — it is what
                      tells you whether +90 was one good night or twelve
                      grinding ones, and it stays in --color-faint so it never
                      competes with the rating beside it. */}
                  <span className="w-[11ch] text-right text-faint">
                    {entry.sessionsCompleted} session{entry.sessionsCompleted === 1 ? '' : 's'}
                  </span>
                </span>
              </li>
            ))}
          </ol>
        )}

        {you && !listed && (
          <div className="mt-6">
            <Result tone="text-faint">
              {you.rank > 0 ? (
                <>
                  you are {you.rank} on{' '}
                  <span className={tone(you.rating)}>
                    {sign(you.rating)}
                    {you.rating}
                  </span>
                </>
              ) : (
                'you have not been judged yet. that is the only way onto this list.'
              )}
            </Result>
          </div>
        )}

        <div className="mt-10 flex items-baseline gap-6">
          <button
            type="button"
            onClick={onBegin}
            className="text-sm lowercase text-accent underline-offset-4 transition-opacity hover:underline"
          >
            <span aria-hidden className="text-faint">$ </span>
            {BOOT_COMMAND}
          </button>
          <button
            type="button"
            onClick={onBack}
            className="text-sm lowercase text-sub underline-offset-4 transition-colors hover:text-ink hover:underline"
          >
            back
          </button>
        </div>
      </div>
    </main>
  )
}
