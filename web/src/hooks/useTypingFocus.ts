import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Drives focus mode (UI-DESIGN.md §6).
 *
 * <p>`typing` goes true on the first keystroke and falls back to false after
 * `idleMs` of silence. It lives once, on the app root, and everything that
 * recedes does so via CSS — no component subscribes to this individually.
 */
export function useTypingFocus(idleMs = 1500) {
  const [typing, setTyping] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  const mark = useCallback(() => {
    setTyping(true)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setTyping(false), idleMs)
  }, [idleMs])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return { typing, mark }
}
