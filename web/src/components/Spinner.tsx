import { useEffect, useState } from 'react'

const FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']

/**
 * A CLI spinner spins at CLI speed. This is the one place UI-DESIGN.md §8's
 * "sparse and slow" does not apply — a dot spinner stepped at the app's
 * ambient pace does not read as a spinner, it reads as a glyph twitching.
 */
const FRAME_MS = 110

function usesReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * The spinner (UI-DESIGN.md §4.1). The only continuously animated thing in the
 * app, and it earns it: it is what makes the status line read as live rather
 * than as a label. Static under reduced motion.
 */
export function Spinner({ className = 'text-faint' }: { className?: string }) {
  const [frame, setFrame] = useState(0)

  useEffect(() => {
    if (usesReducedMotion()) return
    const timer = window.setInterval(() => setFrame((f) => (f + 1) % FRAMES.length), FRAME_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <span aria-hidden className={className}>
      {FRAMES[frame]}
    </span>
  )
}
