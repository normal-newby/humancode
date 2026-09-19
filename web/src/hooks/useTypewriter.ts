import { useEffect, useRef, useState } from 'react'

const TICK_MS = 16
/** However long the line, it finishes in about this long. */
const TARGET_MS = 2800

function instant(): boolean {
  return (
    typeof window === 'undefined' ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * Reveals text a character at a time, the way a model's output arrives
 * (UI-DESIGN.md §4.6).
 *
 * <p>Long lines reveal several characters per tick rather than running slower,
 * so a three-sentence problem statement and a six-word insult both land in
 * roughly the same beat. Under `prefers-reduced-motion` the text is simply
 * there.
 *
 * @param text     the full line; changing it restarts the reveal
 * @param animate  false renders instantly — used for turns that were already
 *                 on screen before this component mounted
 * @param onTick   called on every reveal, so the transcript can keep the
 *                 growing line in view without a re-render per character
 */
export function useTypewriter(
  text: string,
  animate: boolean,
  onTick?: () => void,
): { shown: string; done: boolean } {
  const skip = !animate || instant()
  const [count, setCount] = useState(skip ? text.length : 0)
  const tickRef = useRef(onTick)
  useEffect(() => {
    tickRef.current = onTick
  }, [onTick])

  useEffect(() => {
    if (skip) {
      setCount(text.length)
      return
    }

    setCount(0)
    const step = Math.max(1, Math.ceil(text.length / (TARGET_MS / TICK_MS)))
    const timer = window.setInterval(() => {
      setCount((previous) => {
        if (previous >= text.length) {
          window.clearInterval(timer)
          return previous
        }
        tickRef.current?.()
        return Math.min(text.length, previous + step)
      })
    }, TICK_MS)

    return () => window.clearInterval(timer)
  }, [skip, text])

  return { shown: text.slice(0, count), done: count >= text.length }
}
