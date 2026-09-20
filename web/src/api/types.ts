export type Phase = 'INTRO' | 'CODING' | 'FOLLOWUP' | 'REPORT' | 'DONE'

export type Mood = 'NEUTRAL' | 'AMUSED' | 'IMPATIENT' | 'EXASPERATED' | 'IMPRESSED'

export type EventType = 'EDIT' | 'PASTE' | 'FOCUS' | 'BLUR'

/** What the candidate asks for before starting. */
export type Difficulty = 'very-easy' | 'easy' | 'medium' | 'hard'

/** Whether this session begins with a scaffold or a deliberately broken app. */
export type ProblemType = 'BUILD' | 'BUG_FIX'

export const PROBLEM_TYPES: { value: ProblemType; label: string; description: string }[] = [
  { value: 'BUILD', label: 'build', description: 'start with a scaffold and make the app work' },
  { value: 'BUG_FIX', label: 'bug fix', description: 'find and repair a broken app' },
]

/**
 * The wire value and the word on screen, which differ for exactly one level:
 * `very-easy` travels with a hyphen so it survives the server's enum, and
 * reads as two words in a row of lowercase words.
 */
export const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: 'very-easy', label: 'very easy' },
  { value: 'easy', label: 'easy' },
  { value: 'medium', label: 'medium' },
  { value: 'hard', label: 'hard' },
]

/**
 * One file in the candidate's editor. However many files a problem needs —
 * an HTML/CSS/JS scaffold for something visual, a single file for something
 * simpler — decided once when the problem was authored or generated.
 */
export interface ProblemFile {
  name: string
  /** A Monaco language id, e.g. 'html', 'css', 'javascript', or 'python'. */
  language: string
  starterContent: string
}

/**
 * The candidate-facing view of a problem. The server strips every file's
 * reference content, the rubric and the curveballs before this ever reaches
 * the browser — they only exist inside the interviewer's prompt.
 */
export interface Problem {
  id: string
  title: string
  difficulty: string
  type: ProblemType
  tags: string[]
  statement: string
  files: ProblemFile[]
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

export interface ReportCardStats {
  elapsedSeconds: number
  charsWritten: number
  charsDeleted: number
  pasteCount: number
  submitCount: number
  finalImpatience: number
}

/** The end-of-session report — verdict, insults, begrudging compliments, similar problems. */
export interface ReportCard {
  verdict: string
  insults: string[]
  compliments: string[]
  similarProblems: string[]
  stats: ReportCardStats
  /**
   * How this session moves the candidate's saved rating, -15 to 30. Unlike
   * everything else here, not scoped to this session. Signed in, the server
   * has already applied it and `SessionResponse.user` carries the total;
   * signed out, the client folds it into a localStorage running total
   * (lib/rating.ts) so the corner of the screen still means something.
   */
  ratingDelta: number
  /** True when the model was unavailable, failed, or got rejected by the guard. */
  canned: boolean
}

/**
 * A candidate, across sessions. The server owns every number here — the client
 * never adds a delta to a rating it was given, it just renders what came back
 * from `/finish`.
 */
export interface UserProfile {
  handle: string
  rating: number
  /** The best it has ever been, which a bad session cannot take away. */
  peakRating: number
  sessionsCompleted: number
  /** 1-based, ties shared, `0` for a candidate no session has judged yet. */
  rank: number
}

/** What comes back from claiming a handle. The token is on the wire exactly once. */
export interface ClaimResponse {
  user: UserProfile
  token: string
}

export interface LeaderboardEntry {
  rank: number
  handle: string
  rating: number
  peakRating: number
  sessionsCompleted: number
  lastSeenAt: string
  /** The viewer's own row, marked server-side so the client never matches handles. */
  you: boolean
}

export interface LeaderboardResponse {
  entries: LeaderboardEntry[]
  /** Everyone who has finished a session, not just the rows above. */
  total: number
  /** The viewer's standing, whether or not they made the cut. Null when signed out. */
  you: UserProfile | null
}

export interface SessionResponse {
  sessionId: string
  problem: Problem
  language: string
  phase: Phase
  impatience: number
  transcript: Utterance[]
  notes: string[]
  live: boolean
  /** Only set by the response from `POST /sessions/{id}/finish`. */
  report: ReportCard | null
  /**
   * Whoever the session counts for. On the `/finish` response this is their
   * standing *after* `report.ratingDelta` has landed — the server applies it,
   * so this is the number rather than something to add up here. Null when
   * nobody was signed in.
   */
  user: UserProfile | null
}

/** Response to `POST /sessions/{id}/hint`. Never mixed into the transcript — see App.tsx. */
export interface HintResponse {
  text: string
  hintsUsed: number
  hintsRemaining: number
  /** True when the model was unavailable, failed, or got rejected by the guard. */
  canned: boolean
}

export interface Metrics {
  elapsedSeconds: number
  idleSeconds: number
  charsInserted: number
  charsDeleted: number
  deleteRatio: number
  pasteCount: number
  submitCount: number
  impatience: number
}

export interface TelemetryItem {
  type: EventType
  inserted: number
  deleted: number
  /** Which file the edit happened in. */
  file: string
  detail?: string | null
}

/**
 * One batch of editor telemetry. `files` carries every file's full content,
 * not just whichever one is active — see App.tsx's telemetry flush for why:
 * a batch that only carried the active file could leave another file's
 * server-side copy silently stale if it was edited then switched away from
 * inside the same flush window.
 */
export interface TelemetryBatch {
  events: TelemetryItem[]
  files: Record<string, string>
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
