import { useEffect, useState } from 'react'

const FRAMES = ['✻', '✳', '✢', '✳']

function usesReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * The cycling `✻` (UI-DESIGN.md §4.1). The only continuously animated thing in
 * the app, and it earns it: it is what makes the status line read as live
 * rather than as a label. Static under reduced motion.
 */
export function Spinner({ className = 'text-faint' }: { className?: string }) {
  const [frame, setFrame] = useState(0)

  useEffect(() => {
    if (usesReducedMotion()) return
    const timer = window.setInterval(() => setFrame((f) => (f + 1) % FRAMES.length), 600)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <span aria-hidden className={className}>
      {FRAMES[frame]}
    </span>
  )
}
