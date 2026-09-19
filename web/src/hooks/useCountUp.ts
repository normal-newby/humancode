import { useEffect, useState } from 'react'

/** ms for the count to run its course — brisk, it is one number, not a chart. */
const DURATION_MS = 900

function usesReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * Counts from 0 to `target` once, when `start` turns true. Works for a
 * negative target the same way — it counts down toward it, not up toward
 * zero — since the animation is just `target * easedProgress`.
 *
 * <p>Under reduced motion the value jumps straight to `target`, same
 * convention as BootSequence's own reduced-motion check.
 */
export function useCountUp(target: number, start: boolean): number {
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (!start) return

    if (usesReducedMotion()) {
      setValue(target)
      return
    }

    let frame: number
    const startedAt = performance.now()

    const tick = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / DURATION_MS)
      // Ease-out cubic — quick at first, settles gently, matching the rest
      // of the app's motion (index.css uses ease-out throughout).
      const eased = 1 - (1 - progress) ** 3
      setValue(Math.round(target * eased))
      if (progress < 1) {
        frame = requestAnimationFrame(tick)
      }
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [start, target])

  return value
}
