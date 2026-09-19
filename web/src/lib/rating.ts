const STORAGE_KEY = 'gpdetox.rating'

/**
 * The running total across every session this browser has ever finished.
 *
 * <p>localStorage, not the server — sessions here have no account behind
 * them to hang a persistent number on, so "persistent" means "persistent
 * for this browser." It resets only if the site's storage gets cleared.
 */
export function loadRating(): number {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed = raw === null ? 0 : Number(raw)
    return Number.isFinite(parsed) ? parsed : 0
  } catch {
    // Storage can throw (private browsing, disabled cookies). The rating
    // just does not persist for that visit rather than breaking the app.
    return 0
  }
}

export function saveRating(value: number): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(value))
  } catch {
    // See loadRating — a failed write is not worth surfacing.
  }
}
