import { useState, type FormEvent } from 'react'
import type { UserProfile } from '../api/types'
import type { IdentityStatus } from '../hooks/useIdentity'
import { BOOT_COMMAND } from './BootSequence'
import { Result } from './Transcript'

/** Mirrors `Handles.MAX_LENGTH` on the server. */
const MAX_HANDLE = 16

interface Props {
  status: IdentityStatus
  profile: UserProfile | null
  error: string | null
  onClaim: (handle: string) => void
  onSignOut: () => void
  /** Opens the board. The start screen owns the screen switch. */
  onLeaderboard: () => void
  disabled?: boolean
}

/**
 * Who is playing (start screen only).
 *
 * <p>It is a shell line, not a login form. UI-DESIGN.md §0 refuses anything on
 * this screen that would work as a real sign-in, and this is the shape that
 * keeps its distance: one word typed after a `$`, no password field, no email,
 * nothing to recover. The account it creates is exactly that thin on the server
 * too (`user/User.java`), so the screen is not dressing down something bigger.
 *
 * <p>Signed out it reads as optional and says so, because it is: a session with
 * no handle runs identically and simply moves no row on the board.
 */
export function HandleLine({
  status,
  profile,
  error,
  onClaim,
  onSignOut,
  onLeaderboard,
  disabled = false,
}: Props) {
  const [draft, setDraft] = useState('')
  const busy = status === 'claiming' || status === 'resuming'

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (busy || disabled) return
    onClaim(draft)
  }

  return (
    <section className="mt-8">
      <h2 className="text-xs lowercase text-faint">who is playing</h2>

      {profile ? (
        <div className="mt-2">
          <p className="text-sm lowercase">
            <span className="text-accent">{profile.handle}</span>
            <span aria-hidden className="text-faint">
              {' · '}
            </span>
            {/* Same calm/hot semantics as the meter and the tab: the number is
                the only thing on this screen that carries a judgment. */}
            <span
              className={`tabular-nums ${
                profile.rating > 0 ? 'text-calm' : profile.rating < 0 ? 'text-hot' : 'text-sub'
              }`}
            >
              rating {profile.rating > 0 ? '+' : ''}
              {profile.rating}
            </span>
            <span aria-hidden className="text-faint">
              {' · '}
            </span>
            <span className="text-sub tabular-nums">
              {profile.rank > 0 ? `rank ${profile.rank}` : 'unranked'}
            </span>
          </p>
          <Result tone="text-faint">
            <button
              type="button"
              onClick={onLeaderboard}
              className="lowercase text-accent underline-offset-4 transition-opacity hover:underline"
            >
              the board
            </button>
            <span aria-hidden className="mx-2">
              ·
            </span>
            <button
              type="button"
              onClick={onSignOut}
              disabled={disabled}
              className="lowercase underline-offset-4 transition-colors hover:text-sub hover:underline disabled:opacity-40"
            >
              not you
            </button>
          </Result>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-2">
          <div className="flex items-baseline gap-2">
            {/* The literal command, so what they type reads as its argument
                rather than as a form field: `$ gpdetox --as nimo` is a line
                they could have run, and the start control two blocks down is
                the same `$ gpdetox` without it. */}
            <span aria-hidden className="text-sm whitespace-nowrap text-sub">
              <span className="text-faint">$ </span>
              {BOOT_COMMAND} --as
            </span>
            <label htmlFor="handle" className="sr-only">
              your handle
            </label>
            {/* No border and no fill — §2 bans the box, and a bordered field
                here would be the one piece of form furniture in the app. */}
            <input
              id="handle"
              name="handle"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              disabled={busy || disabled}
              maxLength={MAX_HANDLE}
              autoComplete="off"
              autoCapitalize="off"
              spellCheck={false}
              placeholder="handle"
              className="w-40 bg-transparent text-sm lowercase text-accent caret-accent outline-none placeholder:text-sub focus-visible:underline focus-visible:underline-offset-4 disabled:opacity-40"
            />
            <button
              type="submit"
              disabled={busy || disabled || draft.trim().length === 0}
              className="text-xs lowercase text-sub underline-offset-4 transition-colors hover:text-ink hover:underline disabled:opacity-40"
            >
              <span aria-hidden className="text-faint">⏎ </span>
              {status === 'claiming' ? 'claiming…' : 'claim'}
            </button>
          </div>
          {/* Prose, not an affordance. The way to the board is the
              `$ gpdetox --leaderboard` link down with the start command, where
              a link is expected — a button hidden mid-sentence in a faint
              aside is not a button anybody finds. */}
          <Result tone="text-faint">
            optional. without one, nothing you do lands on the board.
          </Result>
          {error && (
            <p role="alert" className="mt-1 text-xs lowercase text-hot">
              <span aria-hidden>└ </span>
              {error}
            </p>
          )}
        </form>
      )}
    </section>
  )
}
