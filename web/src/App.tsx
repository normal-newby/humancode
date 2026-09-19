import { useCallback, useEffect, useRef, useState } from 'react'
import { finishSession, sendRunResult, startSession } from './api/client'
import type { ReportCard, SessionResponse, TelemetryItem, Utterance } from './api/types'
import { LiveTurn } from './components/LiveTurn'
import type { TurnStamp } from './components/MetaLine'
import { ReportView } from './components/ReportView'
import { StatusLine } from './components/StatusLine'
import { Transcript, type Entry, type PromptEntry } from './components/Transcript'
import { useSessionStream } from './hooks/useSessionStream'
import { useTelemetry } from './hooks/useTelemetry'
import { useTypingFocus } from './hooks/useTypingFocus'
import { runTests, type LocalRunResult } from './lib/runTests'

/** Triggers whose closed turn should carry the `idle Ns` receipt. */
const IDLE_TRIGGERS = new Set(['IDLE', 'NO_START', 'SLOW_PROGRESS'])

/** The log keeps its scrollback, but not unboundedly — see UI-DESIGN.md §4.2. */
const MAX_ENTRIES = 50

/** How long their caret blinks before the prompt it is composing lands. */
const COMPOSING_MS = 850

interface Counters {
  written: number
  deleted: number
  pastes: number
}

const ZERO: Counters = { written: 0, deleted: 0, pastes: 0 }

/** Counters and session-clock reading at the moment the current turn opened. */
interface TurnBase extends Counters {
  atElapsed: number
}

const TURN_ZERO: TurnBase = { ...ZERO, atElapsed: 0 }

export default function App() {
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [starting, setStarting] = useState(false)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [entries, setEntries] = useState<Entry[]>([])
  const [armed, setArmed] = useState(false)
  const [escFlash, setEscFlash] = useState(false)
  const [report, setReport] = useState<ReportCard | null>(null)
  const [finishing, setFinishing] = useState(false)

  /** Utterances waiting behind their typing indicator. */
  const [queue, setQueue] = useState<Utterance[]>([])
  const [composing, setComposing] = useState(false)

  /**
   * Session totals, counted client-side rather than read off the server's
   * metrics: telemetry only flushes every 1.5s, and a footer that lags the
   * typing by a second and a half looks broken.
   */
  const [totals, setTotals] = useState<Counters>(ZERO)
  const totalsRef = useRef<Counters>(ZERO)
  /** Where the turn in progress started, so its stamp is a delta. */
  const [turnBase, setTurnBase] = useState<TurnBase>(TURN_ZERO)
  const turnBaseRef = useRef<TurnBase>(TURN_ZERO)
  /** Last real edit, for the `idle Ns` receipt. Set when the session begins. */
  const lastActivityRef = useRef<number>(0)
  /** Utterances already queued. */
  const seenRef = useRef<Set<string>>(new Set())
  /** Notes already attached to a prompt. */
  const seenNotesRef = useRef(0)
  /** Monotonic, so two turns closing in the same second cannot collide. */
  const turnSeqRef = useRef(0)
  /** Current editor contents, for the test runner. */
  const codeRef = useRef('')
  const startedAtRef = useRef<number | null>(null)

  const sessionId = session?.sessionId ?? null
  const stream = useSessionStream(sessionId)
  const { record, setCode } = useTelemetry(sessionId)
  const { typing, mark } = useTypingFocus()
  /** Readable inside callbacks: were you mid-sentence when they cut in? */
  const typingRef = useRef(false)
  useEffect(() => {
    typingRef.current = typing
  }, [typing])

  const file = session ? `${session.problem.entryPoint}.js` : 'solution.js'

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
      setReport(null)
      const now = Date.now()
      setStartedAt(now)
      startedAtRef.current = now
      setElapsed(0)
      setEntries([])
      setQueue([])
      setComposing(false)
      setTotals(ZERO)
      totalsRef.current = ZERO
      setTurnBase(TURN_ZERO)
      turnBaseRef.current = TURN_ZERO
      lastActivityRef.current = now
      seenRef.current = new Set()
      seenNotesRef.current = 0
      turnSeqRef.current = 0
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
   * Closes the turn in progress and stamps it with what it cost (§4.3). Every
   * prompt they send closes one, the way a user message ends an assistant's
   * turn; `interrupted` is true only when you were actually mid-keystroke.
   */
  const closeTurn = useCallback(
    (interrupted: boolean, idleSeconds: number | null) => {
      const base = turnBaseRef.current
      const current = totalsRef.current
      const nowElapsed = startedAtRef.current
        ? Math.floor((Date.now() - startedAtRef.current) / 1000)
        : 0
      turnSeqRef.current += 1
      const written = current.written - base.written
      const deleted = current.deleted - base.deleted
      const pastes = current.pastes - base.pastes

      setEntries((previous) =>
        [
          ...previous,
          {
            kind: 'turn' as const,
            id: `turn-${turnSeqRef.current}`,
            file,
            empty: written === 0 && deleted === 0 && pastes === 0,
            interrupted,
            stamp: {
              elapsedSeconds: Math.max(0, nowElapsed - base.atElapsed),
              written,
              deleted,
              pastes,
              idleSeconds,
            },
          },
        ].slice(-MAX_ENTRIES),
      )

      const next: TurnBase = { ...current, atElapsed: nowElapsed }
      turnBaseRef.current = next
      setTurnBase(next)
    },
    [file],
  )

  /** Queue new utterances; they land after their caret has blinked at you. */
  useEffect(() => {
    const fresh = stream.utterances.filter((utterance) => !seenRef.current.has(utterance.id))
    if (fresh.length === 0) return
    fresh.forEach((utterance) => seenRef.current.add(utterance.id))
    setQueue((previous) => [...previous, ...fresh])
  }, [stream.utterances])

  // Keyed on the queue alone: a second prompt arriving mid-blink re-arms the
  // timer for the one already waiting rather than cancelling it, which is what
  // guarding on `composing` here would have done.
  useEffect(() => {
    if (queue.length === 0) {
      setComposing(false)
      return
    }
    setComposing(true)
    const timer = window.setTimeout(() => {
      const [next, ...rest] = queue
      const idleSeconds = IDLE_TRIGGERS.has(next.trigger)
        ? Math.round((Date.now() - lastActivityRef.current) / 1000)
        : null

      closeTurn(typingRef.current, idleSeconds)
      setEntries((previous) =>
        [
          ...previous,
          {
            kind: 'prompt' as const,
            id: next.id,
            line: next.line,
            canned: next.canned,
            notes: [],
          },
        ].slice(-MAX_ENTRIES),
      )
      setQueue(rest)
    }, COMPOSING_MS)

    return () => window.clearTimeout(timer)
  }, [closeTurn, queue])

  /** Their notes hang under whichever prompt they were taken during. */
  useEffect(() => {
    const fresh = stream.notes.slice(seenNotesRef.current)
    if (fresh.length === 0) return
    seenNotesRef.current = stream.notes.length

    setEntries((previous) => {
      const index = previous.map((entry) => entry.kind).lastIndexOf('prompt')
      if (index === -1) return previous
      const copy = [...previous]
      const target = copy[index] as PromptEntry
      copy[index] = { ...target, notes: [...target.notes, ...fresh.map((note) => note.note)] }
      return copy
    })
  }, [stream.notes])

  /** Hand the turn back: close it, then let them judge what you handed over. */
  const submit = useCallback(async () => {
    if (!sessionId || !session || running) return
    setRunning(true)
    closeTurn(false, null)
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
      console.warn('[humancode] submit failed', e)
    } finally {
      setRunning(false)
    }
  }, [closeTurn, running, session, sessionId])

  const end = useCallback(async () => {
    if (!sessionId) return
    setFinishing(true)
    try {
      const result = await finishSession(sessionId)
      setReport(result.report)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setFinishing(false)
      setSession(null)
      setStartedAt(null)
      startedAtRef.current = null
      setArmed(false)
    }
  }, [sessionId])

  /**
   * `^d` ends the session, on the second press — the terminal's own way out,
   * and it leaves `esc` alone, which in this layout is theirs (§4.5). `^c`
   * would have been more idiomatic still, but it is copy, and a candidate
   * copying a line should not end their interview.
   */
  useEffect(() => {
    if (!sessionId) return
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setEscFlash(true)
        return
      }
      if (!event.ctrlKey || event.key.toLowerCase() !== 'd') return
      event.preventDefault()
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
    if (!escFlash) return
    const timer = window.setTimeout(() => setEscFlash(false), 2000)
    return () => window.clearTimeout(timer)
  }, [escFlash])

  useEffect(() => {
    if (!sessionId) return
    const handler = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [sessionId])

  if (report) {
    return <ReportView report={report} onRestart={() => setReport(null)} />
  }

  if (!session) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-[52ch]">
          <h1 className="text-2xl lowercase tracking-tight text-ink">humancode</h1>
          <p className="mt-4 text-sm leading-relaxed text-sub">
            the interview, inverted. they prompt. you generate. they watch the tokens go by and
            form opinions.
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

  // The live stamp under your editor: what this turn has cost so far. Session
  // totals belong to the footer; everything in the log is a delta.
  const liveStamp: TurnStamp = {
    elapsedSeconds: Math.max(0, elapsed - turnBase.atElapsed),
    written: totals.written - turnBase.written,
    deleted: totals.deleted - turnBase.deleted,
    pastes: totals.pastes - turnBase.pastes,
    idleSeconds: null,
  }

  return (
    <div data-typing={typing} className="flex h-screen flex-col bg-canvas text-ink">
      <Transcript
        statement={session.problem.statement}
        entries={entries}
        incoming={composing}
        connected={stream.connected}
      />

      <div className="shrink-0">
        <LiveTurn
          file={file}
          language={session.language}
          initialCode={session.problem.starterCode}
          stamp={liveStamp}
          onTelemetry={handleTelemetry}
          onCodeChange={handleCodeChange}
          onSubmit={submit}
        />

        <StatusLine
          elapsedSeconds={elapsed}
          totals={totals}
          impatience={stream.impatience}
          activity={running ? 'running' : typing ? 'writing' : 'idle'}
          running={running}
          finishing={finishing}
          armed={armed}
          escFlash={escFlash}
          onSubmit={submit}
          onEnd={end}
        />
      </div>
    </div>
  )
}
