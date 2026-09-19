const GRADIENT =
  'linear-gradient(90deg, var(--color-calm) 0%, var(--color-warm) 55%, var(--color-hot) 100%)'

/**
 * Green is calm, red is furious (UI-DESIGN.md §5).
 *
 * <p>The gradient sits on the full-width track and a canvas-coloured cover eats
 * the unfilled portion from the right. Putting the gradient on a growing fill
 * instead would render green-to-red at every value, which makes the colour mean
 * nothing — at 20 you must see green only.
 */
export function ImpatienceMeter({ impatience }: { impatience: number }) {
  const clamped = Math.min(100, Math.max(0, impatience))
  const furious = clamped >= 85

  return (
    <div>
      <div className="mb-2 text-[11px] lowercase tracking-wide text-sub">impatience</div>
      <div
        className="relative h-2 w-full overflow-hidden rounded-full"
        style={{ background: GRADIENT }}
        role="meter"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="interviewer impatience"
      >
        {/* Masks the unreached portion of the gradient. */}
        <div
          className="absolute inset-y-0 right-0 bg-surface transition-[width] duration-700 ease-out"
          style={{ width: `${100 - clamped}%` }}
        />
        {furious && (
          <div
            className="animate-meter-pulse pointer-events-none absolute inset-0"
            style={{ background: GRADIENT, width: `${clamped}%` }}
            aria-hidden
          />
        )}
      </div>
      <div className="mt-1.5 text-right text-xs tabular-nums text-sub">{clamped}</div>
    </div>
  )
}
