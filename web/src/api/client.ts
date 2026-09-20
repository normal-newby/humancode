import type {
  ClaimResponse,
  Difficulty,
  ProblemType,
  HintResponse,
  LeaderboardResponse,
  Metrics,
  SessionResponse,
  TelemetryBatch,
  UserProfile,
} from './types'
import type { Identity } from '../lib/identity'

/**
 * Everything is relative to `/api`, which Vite proxies to Spring on :8080 in
 * dev and which is same-origin in the packaged jar. There is deliberately no
 * base URL to configure.
 */
const BASE = '/api'

/**
 * A failed call, with the status and the server's own body kept apart.
 *
 * <p>The handle endpoints answer with a sentence written to be shown to the
 * candidate verbatim (`that handle is taken`), and the start screen prints it
 * under their handle line. A single `message` string with the method, the path
 * and the status code folded into it would have put `POST /users failed: 409`
 * on screen, so `detail` stays separate from what a thrown Error reads like in
 * a console.
 */
export class ApiError extends Error {
  readonly status: number
  /** The server's body, or '' — safe to render as-is. */
  readonly detail: string

  constructor(method: string, path: string, status: number, detail: string) {
    super(`${method} ${path} failed: ${status} ${detail}`.trim())
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new ApiError(init?.method ?? 'GET', path, response.status, body.trim())
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

/**
 * `identity` rides along so the finished session can move a rating. A handle
 * whose token no longer verifies does not fail the request — the server starts
 * an anonymous session instead, because losing your rating for the evening is a
 * smaller problem than losing your turn at the keyboard.
 */
export function startSession(options: {
  problemId?: string
  language?: string
  difficulty?: Difficulty
  problemType?: ProblemType
  identity?: Identity | null
}): Promise<SessionResponse> {
  const { identity, ...rest } = options
  return request<SessionResponse>('/sessions', {
    method: 'POST',
    body: JSON.stringify({ ...rest, handle: identity?.handle, token: identity?.token }),
  })
}

/**
 * Claims a handle nobody holds. 409 when somebody already does, 400 when it is
 * not a handle — both come back with a sentence written to be shown verbatim.
 */
export function claimHandle(handle: string): Promise<ClaimResponse> {
  return request<ClaimResponse>('/users', {
    method: 'POST',
    body: JSON.stringify({ handle }),
  })
}

/** Signs a browser back in with the token it kept. 403 if the pair is wrong. */
export function resumeHandle(identity: Identity): Promise<UserProfile> {
  return request<UserProfile>('/users/resume', {
    method: 'POST',
    body: JSON.stringify(identity),
  })
}

/**
 * The board. `handle` is the viewer, so the server can mark their own row and
 * report their standing even when they are below the cut — it proves nothing
 * and is not meant to.
 */
export function fetchLeaderboard(handle?: string | null, limit = 20): Promise<LeaderboardResponse> {
  const query = new URLSearchParams({ limit: String(limit) })
  if (handle) query.set('handle', handle)
  return request<LeaderboardResponse>(`/leaderboard?${query}`)
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
