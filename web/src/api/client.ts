import type {
  Difficulty,
  ProblemType,
  HintResponse,
  Metrics,
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
  problemType?: ProblemType
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

/**
 * Hands the turn back. There is nothing to run locally anymore (CLAUDE.md §6)
 * — the interviewer judges the current diff against the rubric, the same way
 * every other reaction works.
 */
export function submitTurn(sessionId: string): Promise<Metrics> {
  return request<Metrics>(`/sessions/${sessionId}/submit`, { method: 'POST' })
}

/**
 * Asks for a hint directly. Capped server-side at `SessionState.MAX_HINTS` —
 * a 409 past that is a defensive backstop, not the normal path, since the
 * client disables the button once `hintsRemaining` hits zero.
 */
export function requestHint(sessionId: string): Promise<HintResponse> {
  return request<HintResponse>(`/sessions/${sessionId}/hint`, { method: 'POST' })
}

export function finishSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/sessions/${sessionId}/finish`, { method: 'POST' })
}

export function streamUrl(sessionId: string): string {
  return `${BASE}/sessions/${sessionId}/stream`
}
