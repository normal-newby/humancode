const STORAGE_KEY = 'gpdetox.identity'

/**
 * The whole account, as the browser holds it.
 *
 * <p>There is no password and nothing to reset: claiming a handle mints a
 * token, this is where the token lives, and the pair is what proves a session
 * belongs to you. That is a deliberate ceiling rather than an unfinished auth
 * system — see `user/User.java`. What it costs is worth stating plainly,
 * because a candidate will hit it at some point: clear this browser's storage
 * and the handle is gone for good, since nothing else in the world knows the
 * token. Pick another one.
 */
export interface Identity {
  handle: string
  token: string
}

export function loadIdentity(): Identity | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      typeof (parsed as Identity).handle === 'string' &&
      typeof (parsed as Identity).token === 'string'
    ) {
      return parsed as Identity
    }
    return null
  } catch {
    // Storage can throw (private browsing, disabled cookies) and the stored
    // value can be anything at all. Either way the session runs anonymously
    // rather than the app refusing to start.
    return null
  }
}

export function saveIdentity(identity: Identity): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
  } catch {
    // See loadIdentity — a failed write costs the handle, not the session.
  }
}

export function clearIdentity(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to do about it, and nothing worth showing them.
  }
}
