import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, claimHandle, resumeHandle } from '../api/client'
import type { UserProfile } from '../api/types'
import { clearIdentity, loadIdentity, saveIdentity, type Identity } from '../lib/identity'

export type IdentityStatus = 'anonymous' | 'resuming' | 'claiming' | 'signed-in'

/**
 * Who is playing, for as long as the tab is open.
 *
 * <p>Two rules it exists to keep in one place:
 *
 * <ul>
 *   <li><b>The profile is the server's, never the client's arithmetic.</b>
 *       `adopt` is how a finished session hands back the standing the server
 *       just computed. Nothing here ever adds a rating delta to a rating.</li>
 *   <li><b>A stale identity signs out quietly.</b> A token that no longer
 *       verifies is a cleared database or a wiped row, not something a
 *       candidate can act on — so it drops the stored pair and lets them claim
 *       again rather than showing them a failure they cannot fix.</li>
 * </ul>
 */
export function useIdentity() {
  const [identity, setIdentity] = useState<Identity | null>(() => loadIdentity())
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [status, setStatus] = useState<IdentityStatus>(() =>
    loadIdentity() === null ? 'anonymous' : 'resuming',
  )
  const [error, setError] = useState<string | null>(null)
  /** StrictMode mounts effects twice in dev; one resume per stored pair is enough. */
  const resumed = useRef(false)

  useEffect(() => {
    const stored = loadIdentity()
    if (!stored || resumed.current) return
    resumed.current = true

    let live = true
    resumeHandle(stored)
      .then((user) => {
        if (!live) return
        setProfile(user)
        setStatus('signed-in')
      })
      .catch((e: unknown) => {
        if (!live) return
        // 403 is the only answer that means "this pair is dead". A network
        // blip should not cost them their handle, so anything else keeps it
        // and simply leaves the standing unknown until the next call.
        if (e instanceof ApiError && e.status === 403) {
          clearIdentity()
          setIdentity(null)
          setStatus('anonymous')
          return
        }
        setStatus('signed-in')
      })
    return () => {
      live = false
    }
  }, [])

  const claim = useCallback(async (handle: string) => {
    const trimmed = handle.trim()
    if (!trimmed) return
    setStatus('claiming')
    setError(null)
    try {
      const claimed = await claimHandle(trimmed)
      const next: Identity = { handle: claimed.user.handle, token: claimed.token }
      saveIdentity(next)
      setIdentity(next)
      setProfile(claimed.user)
      setStatus('signed-in')
    } catch (e) {
      // The server's body is written to be read by the candidate, so it goes
      // on screen as-is; anything else is a network failure and gets the one
      // sentence this screen can honestly say about it.
      setError(e instanceof ApiError && e.detail ? e.detail : 'could not reach the leaderboard')
      setStatus('anonymous')
    }
  }, [])

  const signOut = useCallback(() => {
    clearIdentity()
    setIdentity(null)
    setProfile(null)
    setError(null)
    setStatus('anonymous')
  }, [])

  /** What the server says they are now, after a session it just judged. */
  const adopt = useCallback((user: UserProfile) => setProfile(user), [])

  return { identity, profile, status, error, claim, signOut, adopt }
}
