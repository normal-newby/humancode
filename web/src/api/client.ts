import type {
  Difficulty,
  Metrics,
  RunResult,
  SessionResponse,
  TelemetryBatch,
} from './types'

/**
 * Everything is relative to `/api`, which Vite proxies to Spring on :8080 in
 * dev and which is same-origin in the packaged jar. There is deliberately no
 * base URL to configure.
 */
const BASE = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`${init?.method ?? 'GET'} ${path} failed: ${response.status} ${body}`.trim())
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function startSession(options: {
  problemId?: string
  language?: string
  difficulty?: Difficulty
}): Promise<SessionResponse> {
  return request<SessionResponse>('/sessions', {
    method: 'POST',
    body: JSON.stringify(options),
  })
}

export function getSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/sessions/${sessionId}`)
}

export function sendTelemetry(sessionId: string, batch: TelemetryBatch): Promise<Metrics> {
  return request<Metrics>(`/sessions/${sessionId}/telemetry`, {
    method: 'POST',
    body: JSON.stringify(batch),
  })
}

export function sendRunResult(sessionId: string, result: RunResult): Promise<Metrics> {
  return request<Metrics>(`/sessions/${sessionId}/run`, {
    method: 'POST',
    body: JSON.stringify(result),
  })
}

export function finishSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/sessions/${sessionId}/finish`, { method: 'POST' })
}

export function streamUrl(sessionId: string): string {
  return `${BASE}/sessions/${sessionId}/stream`
}
