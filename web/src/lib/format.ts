/** Shared number formatting for the meta lines and the status line. */

/** `mm:ss`, zero-padded. Hours are not a thing that happens here. */
export function clock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/**
 * Characters, formatted the way a token readout formats tokens: thousands as
 * `1.2k`, one decimal, no unit word (UI-DESIGN.md §5).
 */
export function compact(value: number): string {
  if (value < 1000) return String(value)
  return `${(value / 1000).toFixed(1)}k`
}
