import { useEffect, useMemo, useState } from 'react'

/** What the candidate is visibly doing right now. */
export type Activity = 'writing' | 'idle' | 'running'

/** Rotates on this cadence while the state holds. */
const ROTATE_MS = 3500

const WRITING = ['writing', 'typing', 'scribbling', 'composing', 'hacking', 'cranking']
const IDLE = ['thinking', 'contemplating', 'pondering', 'deliberating', 'ruminating', 'mulling']
/** Above the impatience threshold it stops being generous about the pause. */
const IDLE_HOT = ['stalling', 'hesitating', 'stewing', 'wavering', 'reconsidering']
const RUNNING = ['running', 'checking', 'judging']

const HOT_AT = 60

/**
 * The word in the status line (UI-DESIGN.md §4.5).
 *
 * <p>The inversion, in one word: a coding agent's spinner narrates what *it* is
 * doing, and this one narrates what *you* are doing, because in this room you
 * are the one being waited on. It re-picks on every state change and then every
 * few seconds, so the bottom of the screen is never quite still.
 */
export function useActivity(activity: Activity, impatience: number): string {
  const hot = impatience >= HOT_AT

  const pool = useMemo(() => {
    if (activity === 'running') return RUNNING
    if (activity === 'writing') return WRITING
    return hot ? IDLE_HOT : IDLE
  }, [activity, hot])

  const [word, setWord] = useState(pool[0])

  useEffect(() => {
    const pick = (previous: string) => {
      const options = pool.filter((candidate) => candidate !== previous)
      return options[Math.floor(Math.random() * options.length)] ?? pool[0]
    }
    setWord(pick)
    const timer = window.setInterval(() => setWord(pick), ROTATE_MS)
    return () => window.clearInterval(timer)
  }, [pool])

  return word
}
