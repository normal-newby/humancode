import { useCallback, useEffect, useRef, useState } from 'react'
import { finishSession, sendRunResult, startSession } from './api/client'
import type { SessionResponse, TelemetryItem } from './api/types'
import { EditorPane } from './components/EditorPane'
import type { TurnStamp } from './components/MetaLine'
import { StatusLine } from './components/StatusLine'
import { Transcript, type Entry } from './components/Transcript'
import { useSessionStream } from './hooks/useSessionStream'
import { useTelemetry } from './hooks/useTelemetry'
import { useTypingFocus } from './hooks/useTypingFocus'
import { runTests, type LocalRunResult } from './lib/runTests'

/** Triggers whose meta line should carry the `idle Ns` receipt. */
const IDLE_TRIGGERS = new Set(['IDLE', 'NO_START', 'SLOW_PROGRESS'])

/** The log keeps its scrollback, but not unboundedly — see UI-DESIGN.md §4.2. */
const MAX_ENTRIES = 50

interface Counters {
  written: number
  deleted: number
  pastes: number
}

const ZERO: Counters = { written: 0, deleted: 0, pastes: 0 }

export default function App() {
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [starting, setStarting] = useState(false)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [entries, setEntries] = useState<Entry[]>([])
  const [armed, setArmed] = useState(false)

  /**
   * Session totals, counted client-side rather than read off the server's
   * metrics: telemetry only flushes every 1.5s, and a status line that lags
   * the typing by a second and a half looks broken.
   */
  const [totals, setTotals] = useState<Counters>(ZERO)
  const totalsRef = useRef<Counters>(ZERO)
  /** Totals as of the previous turn, so each meta line can show a delta. */
  const previousRef = useRef<Counters>(ZERO)
  /** Last real edit, for the `idle Ns` receipt. Set when the session begins. */
  const lastActivityRef = useRef<number>(0)
  /** Utterances already turned into entries. */
  const seenRef = useRef<Set<string>>(new Set())
  /** Current editor contents, for the test runner. */
  const codeRef = useRef('')
  const startedAtRef = useRef<number | null>(null)

  const sessionId = session?.sessionId ?? null
  const stream = useSessionStream(sessionId)
  const { record, setCode } = useTelemetry(sessionId)
  const { typing, mark } = useTypingFocus()

  const handleCodeChange = useCallback(
    (code: string) => {
      codeRef.current = code
      setCode(code)
    },
    [setCode],
  )

  /** Only real work counts as typing — focus/blur must not dim the log. */
  const handleTelemetry = useCallback(
    (item: TelemetryItem) => {
      if (item.type === 'EDIT' || item.type === 'PASTE') {
        mark()
        lastActivityRef.current = Date.now()

        // A paste also arrives as an EDIT, so only count the pastes here.
        const next: Counters =
          item.type === 'PASTE'
            ? { ...totalsRef.current, pastes: totalsRef.current.pastes + 1 }
            : {
                ...totalsRef.current,
                written: totalsRef.current.written + item.inserted,
                deleted: totalsRef.current.deleted + item.deleted,
              }
        totalsRef.current = next
        setTotals(next)
      }
      record(item)
    },
    [mark, record],
  )

  const begin = useCallback(async () => {
    setStarting(true)
    setError(null)
    try {
      const started = await startSession({})
      setSession(started)
      const now = Date.now()
      setStartedAt(now)
      startedAtRef.current = now
      setElapsed(0)
      setEntries([])
      setTotals(ZERO)
      totalsRef.current = ZERO
      previousRef.current = ZERO
      lastActivityRef.current = now
      seenRef.current = new Set()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setStarting(false)
    }
  }, [])

  // Local clock: telemetry only flushes when there are events, so the server's
  // elapsed count stalls the moment you stop typing — which is exactly when the
  // clock matters most.
  useEffect(() => {
    if (!startedAt) return
    const timer = window.setInterval(
      () => setElapsed(Math.floor((Date.now() - startedAt) / 1000)),
      1000,
    )
    return () => window.clearInterval(timer)
  }, [startedAt])

  /**
   * Stamps each new turn as it arrives (UI-DESIGN.md §5). The numbers are what
   * the candidate did *since the previous turn* — the analogue of a per-message
   * token count. Totals stay in the status line.
   */
  useEffect(() => {
    const fresh = stream.utterances.filter((utterance) => !seenRef.current.has(utterance.id))
    if (fresh.length === 0) return

    const now = Date.now()
    const additions: Entry[] = fresh.map((utterance) => {
      seenRef.current.add(utterance.id)
      const current = totalsRef.current
      const previous = previousRef.current
      const stamp: TurnStamp = {
        elapsedSeconds: startedAtRef.current
          ? Math.floor((now - startedAtRef.current) / 1000)
          : 0,
        written: current.written - previous.written,
        deleted: current.deleted - previous.deleted,
        pastes: current.pastes - previous.pastes,
        idleSeconds: IDLE_TRIGGERS.has(utterance.trigger)
          ? Math.round((now - lastActivityRef.current) / 1000)
          : null,
      }
      previousRef.current = current
      return { id: utterance.id, line: utterance.line, canned: utterance.canned, stamp }
    })

    setEntries((current) => [...current, ...additions].slice(-MAX_ENTRIES))
  }, [stream.utterances])

  const run = useCallback(async () => {
    if (!sessionId || !session || running) return
    setRunning(true)
    try {
      // Executed in a throwaway Web Worker, with a hard timeout — see
      // lib/runTests.ts. The verdict below is the real one.
      const result: LocalRunResult = await runTests(session.problem, codeRef.current)

      // The verdict is never shown. It goes to the server as a gauge of
      // progress and the interviewer decides what to do with it — a pass/fail
      // count on screen is the scoreboard this product is built to avoid.
      await sendRunResult(sessionId, {
        passed: result.passed,
        passedCount: result.passedCount,
        failedCount: result.failedCount,
        firstFailure: result.firstFailure,
        durationMs: result.durationMs,
      })
    } catch (e) {
      console.warn('[humancode] run failed', e)
    } finally {
      setRunning(false)
    }
  }, [running, session, sessionId])

  const end = useCallback(async () => {
    if (!sessionId) return
    try {
      await finishSession(sessionId)
    } finally {
      setSession(null)
      setStartedAt(null)
      startedAtRef.current = null
      setArmed(false)
    }
  }, [sessionId])

  /**
   * `esc` ends the session — but only on the second press, the way a terminal
   * agent asks you to confirm. A stray Escape inside the editor must not throw
   * away an interview.
   */
  useEffect(() => {
    if (!sessionId) return
    const handler = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setArmed((previous) => {
        if (previous) {
          void end()
          return false
        }
        return true
      })
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [end, sessionId])

  useEffect(() => {
    if (!armed) return
    const timer = window.setTimeout(() => setArmed(false), 3000)
    return () => window.clearTimeout(timer)
  }, [armed])

  useEffect(() => {
    if (!sessionId) return
    const handler = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [sessionId])

  if (!session) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-[52ch]">
          <h1 className="text-2xl lowercase tracking-tight text-ink">humancode</h1>
          <p className="mt-4 text-sm leading-relaxed text-sub">
            the interview, inverted. it asks the question. you write the code. it watches you
            type and forms opinions.
          </p>
          <button
            type="button"
            onClick={begin}
            disabled={starting}
            className="mt-10 text-sm lowercase text-accent underline-offset-4 transition-opacity hover:underline disabled:opacity-40"
          >
            {starting ? 'finding someone to judge you…' : 'begin'}
          </button>
          {error && <p className="mt-6 text-xs text-hot">{error}</p>}
        </div>
      </main>
    )
  }

  // The live stamp under the pinned statement: session totals, ticking. Every
  // other meta line froze the instant its turn was spoken.
  const liveStamp: TurnStamp = {
    elapsedSeconds: elapsed,
    written: totals.written,
    deleted: totals.deleted,
    pastes: totals.pastes,
    idleSeconds: null,
  }

  return (
    <div data-typing={typing} className="flex h-screen flex-col bg-canvas text-ink">
      <Transcript
        problem={session.problem}
        entries={entries}
        liveStamp={liveStamp}
        notes={stream.notes}
        busy={running}
        busyLabel="running tests…"
        connected={stream.connected}
      />

      <div className="shrink-0">
        <EditorPane
          language={session.language}
          initialCode={session.problem.starterCode}
          onTelemetry={handleTelemetry}
          onCodeChange={handleCodeChange}
          onRun={run}
        />

        <StatusLine
          elapsedSeconds={elapsed}
          totals={totals}
          impatience={stream.impatience}
          activity={running ? 'running' : typing ? 'writing' : 'idle'}
          running={running}
          armed={armed}
          onRun={run}
          onEnd={end}
        />
      </div>
    </div>
  )
}
