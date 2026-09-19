import { InterviewerFace } from './InterviewerFace'

const GRADIENT =
  'linear-gradient(90deg, var(--color-calm) 0%, var(--color-warm) 55%, var(--color-hot) 100%)'

/**
 * Green is calm, red is furious (UI-DESIGN.md §6). It is the patience of the
 * human on the other side, which is why it says so — an unlabelled meter in
 * this layout reads as something about the model, and it is the opposite.
 *
 * <p>The gradient sits on the full-width track and a surface-coloured cover
 * eats the unfilled portion from the right. Putting the gradient on a growing
 * fill instead would render green-to-red at every value, which makes the colour
 * mean nothing — at 20 you must see green only.
 *
 * <p>At ≥85 the number pulses, not the bar: at this size a pulsing bar is
 * invisible and a pulsing number is not.
 */
export function ImpatienceMeter({ impatience }: { impatience: number }) {
  const clamped = Math.min(100, Math.max(0, Math.round(impatience)))
  const furious = clamped >= 85

  return (
    <span className="flex items-center gap-2">
      <InterviewerFace impatience={clamped} />
      <span className="lowercase">human impatience</span>
      <span
        className={`tabular-nums ${furious ? 'animate-meter-pulse text-hot' : 'text-sub'}`}
      >
        {clamped}%
      </span>
      <span
        className="relative h-2 w-24 overflow-hidden rounded-full"
        style={{ background: GRADIENT }}
        role="meter"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="human impatience"
      >
        {/* Masks the unreached portion of the gradient. */}
        <span
          className="absolute inset-y-0 right-0 bg-surface transition-[width] duration-700 ease-out"
          style={{ width: `${100 - clamped}%` }}
        />
      </span>
    </span>
  )
}
