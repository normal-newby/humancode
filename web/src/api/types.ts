export type Phase = 'INTRO' | 'CODING' | 'FOLLOWUP' | 'REPORT' | 'DONE'

export type Mood = 'NEUTRAL' | 'AMUSED' | 'IMPATIENT' | 'EXASPERATED' | 'IMPRESSED'

export type EventType = 'EDIT' | 'PASTE' | 'RUN' | 'FOCUS' | 'BLUR'

export interface ProblemExample {
  input: string
  output: string
  explanation: string | null
}

/**
 * The candidate-facing view of a problem. The server strips the reference
 * solution and rubric before this ever reaches the browser — they only exist
 * inside the interviewer's prompt.
 */
export interface TestCase {
  args: unknown[]
  expected: unknown
}

export interface Problem {
  id: string
  title: string
  difficulty: string
  tags: string[]
  statement: string
  examples: ProblemExample[]
  starterCode: string
  /** The function the test runner calls. */
  entryPoint: string
  tests: TestCase[]
  match: 'exact' | 'unordered'
}

export interface Utterance {
  id: string
  at: string
  trigger: string
  line: string
  mood: Mood
  impatienceAfter: number
  /** True when the line came from the canned fallback rather than the model. */
  canned: boolean
}

export interface SessionResponse {
  sessionId: string
  problem: Problem
  persona: string
  language: string
  phase: Phase
  impatience: number
  transcript: Utterance[]
  notes: string[]
  live: boolean
}

export interface Metrics {
  elapsedSeconds: number
  idleSeconds: number
  charsInserted: number
  charsDeleted: number
  deleteRatio: number
  pasteCount: number
  runCount: number
  failedRunCount: number
  impatience: number
}

export interface TelemetryItem {
  type: EventType
  inserted: number
  deleted: number
  detail?: string | null
}

export interface TelemetryBatch {
  events: TelemetryItem[]
  code: string
}

export interface RunResult {
  passed: boolean
  passedCount: number
  failedCount: number
  firstFailure: string | null
  durationMs: number
}

/** Payloads pushed over SSE, keyed by event name. */
export interface MeterPayload {
  impatience: number
  mood: Mood
}

export interface NotePayload {
  note: string
  at: string
}

export interface PhasePayload {
  phase: Phase
}
