import { useCallback, useEffect, useRef, useState } from 'react'
import { sendTelemetry } from '../api/client'
import type { Metrics, TelemetryItem } from '../api/types'

const FLUSH_INTERVAL_MS = 1500

/**
 * Buffers editor events and ships them in batches.
 *
 * <p>One request per keystroke would hammer the server for no benefit — the
 * backend's trigger engine works off accumulated state, not individual edits.
 * The code snapshot rides along with each batch so the interviewer always sees
 * current contents without a separate fetch.
 */
export function useTelemetry(sessionId: string | null) {
  const buffer = useRef<TelemetryItem[]>([])
  /** Filename -> latest content. Every file rides along on every flush — see TelemetryBatch. */
  const latestFiles = useRef<Record<string, string>>({})
  const inFlight = useRef(false)
  /**
   * Set once the session is deliberately over. The server drops the session
   * from its live map on `/finish`, so anything sent after that 404s — and the
   * unmount flush below would send exactly one such batch every single time.
   */
  const stopped = useRef(false)
  const [metrics, setMetrics] = useState<Metrics | null>(null)

  const record = useCallback((item: TelemetryItem) => {
    buffer.current.push(item)
  }, [])

  const setCode = useCallback((file: string, code: string) => {
    latestFiles.current = { ...latestFiles.current, [file]: code }
  }, [])

  const flush = useCallback(async () => {
    if (!sessionId || stopped.current) return
    // Skip empty batches, but always send if we have never synced the code.
    if (buffer.current.length === 0) return
    if (inFlight.current) return

    const events = buffer.current
    buffer.current = []
    inFlight.current = true

    try {
      const result = await sendTelemetry(sessionId, {
        events,
        files: latestFiles.current,
      })
      setMetrics(result)
    } catch (error) {
      // Put the events back so a blip does not lose the replay log.
      buffer.current = [...events, ...buffer.current]
      console.warn('[humancode] telemetry flush failed', error)
    } finally {
      inFlight.current = false
    }
  }, [sessionId])

  /**
   * Close the pipe for good. Call it after a final {@link flush} and before the
   * session id goes away, or the unmount flush races `/finish`.
   */
  const stop = useCallback(() => {
    stopped.current = true
    buffer.current = []
  }, [])

  useEffect(() => {
    if (!sessionId) return
    stopped.current = false

    const timer = window.setInterval(() => void flush(), FLUSH_INTERVAL_MS)
    return () => {
      window.clearInterval(timer)
      void flush()
    }
  }, [flush, sessionId])

  return { record, setCode, metrics, flush, stop }
}
