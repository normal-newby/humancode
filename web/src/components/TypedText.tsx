import { useEffect } from 'react'
import { useTypewriter } from '../hooks/useTypewriter'

interface Props {
  text: string
  /** False for turns already on screen before this mounted. */
  animate: boolean
  className?: string
  onTick?: () => void
  onDone?: () => void
}

/**
 * A line the interviewer types out (UI-DESIGN.md §4.6).
 *
 * <p>The full text is always in the DOM for screen readers — the live region
 * announces it once, rather than re-announcing a growing prefix sixty times a
 * second — and the visible, animating copy is `aria-hidden`.
 */
export function TypedText({ text, animate, className, onTick, onDone }: Props) {
  const { shown, done } = useTypewriter(text, animate, onTick)

  useEffect(() => {
    if (done) onDone?.()
    // onDone is a stable callback from the parent; re-firing on identity churn
    // would spam the caller.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [done])

  return (
    <p className={className}>
      <span className="sr-only">{text}</span>
      <span aria-hidden>
        {shown}
        {!done && (
          <span className="ml-0.5 inline-block translate-y-[0.1em] text-accent select-none">
            ▍
          </span>
        )}
      </span>
    </p>
  )
}
