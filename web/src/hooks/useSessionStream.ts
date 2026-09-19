import { useEffect, useState } from 'react'
import { streamUrl } from '../api/client'
import type { Mood, NotePayload, Phase, Utterance } from '../api/types'

interface StreamState {
  connected: boolean
  utterances: Utterance[]
  notes: NotePayload[]
  impatience: number
  mood: Mood
  phase: Phase | null
}

const INITIAL: StreamState = {
  connected: false,
  utterances: [],
  notes: [],
  impatience: 0,
  mood: 'NEUTRAL',
  phase: null,
}

/**
 * Subscribes to the interviewer's SSE channel.
 *
 * <p>The server suppresses triggers when nobody is listening, so this stream
 * being open is what makes the interviewer speak at all.
 */
export function useSessionStream(sessionId: string | null): StreamState {
  const [state, setState] = useState<StreamState>(INITIAL)

  useEffect(() => {
    if (!sessionId) {
      setState(INITIAL)
      return
    }

    const source = new EventSource(streamUrl(sessionId))

    source.addEventListener('connected', () => {
      setState((prev) => ({ ...prev, connected: true }))
    })

    source.addEventListener('utterance', (event) => {
      const utterance = JSON.parse((event as MessageEvent).data) as Utterance
      setState((prev) => ({
        ...prev,
        utterances: [...prev.utterances, utterance],
        mood: utterance.mood,
      }))
    })

    source.addEventListener('meter', (event) => {
      const payload = JSON.parse((event as MessageEvent).data) as {
        impatience: number
        mood: Mood
      }
      setState((prev) => ({ ...prev, impatience: payload.impatience, mood: payload.mood }))
    })

    source.addEventListener('note', (event) => {
      const note = JSON.parse((event as MessageEvent).data) as NotePayload
      setState((prev) => ({ ...prev, notes: [...prev.notes, note] }))
    })

    source.addEventListener('phase', (event) => {
      const payload = JSON.parse((event as MessageEvent).data) as { phase: Phase }
      setState((prev) => ({ ...prev, phase: payload.phase }))
    })

    source.onerror = () => {
      // EventSource reconnects on its own; just reflect it in the UI.
      setState((prev) => ({ ...prev, connected: false }))
    }

    return () => source.close()
  }, [sessionId])

  return state
}
