import { useCallback, useEffect, useRef, useState } from 'react'
import { finishSession, startSession, submitTurn } from './api/client'
import type {
  Difficulty,
  ProblemType,
  ProblemFile,
  ReportCard,
  SessionResponse,
  TelemetryItem,
  Utterance,
} from './api/types'
import { DifficultyPicker } from './components/DifficultyPicker'
import { LiveTurn } from './components/LiveTurn'
import { ProblemTypePicker } from './components/ProblemTypePicker'
import type { TurnStamp } from './components/MetaLine'
import { ReportView } from './components/ReportView'
import { StatusLine } from './components/StatusLine'
import { Transcript, type Entry, type PromptEntry } from './components/Transcript'
import { useSessionStream } from './hooks/useSessionStream'
import { useTelemetry } from './hooks/useTelemetry'
import { useTypingFocus } from './hooks/useTypingFocus'

/** Beyond this, the closed-turn label collapses to a count rather than naming every file. */
const MAX_NAMED_FILES_IN_LABEL = 2

/** Stable reference so `files` does not look like a new value on every render before a session exists. */
const NO_FILES: ProblemFile[] = []

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
  /** Their choice, sent with the session. Medium is the honest default. */
  const [difficulty, setDifficulty] = useState<Difficulty>('medium')
  const [problemType, setProblemType] = useState<ProblemType>('BUILD')
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
  /** Files touched (EDIT or PASTE) since the current turn opened. */
  const touchedFilesRef = useRef<Set<string>>(new Set())
  const startedAtRef = useRef<number | null>(null)

  const sessionId = session?.sessionId ?? null
  const stream = useSessionStream(sessionId)
  const { record, setCode, flush, stop } = useTelemetry(sessionId)
  const { typing, mark } = useTypingFocus()
  /** Readable inside callbacks: were you mid-sentence when they cut in? */
  const typingRef = useRef(false)
  useEffect(() => {
    typingRef.current = typing
  }, [typing])

  const files = session?.problem.files ?? NO_FILES

  const handleCodeChange = useCallback(
    (file: string, code: string) => {
      setCode(file, code)
    },
    [setCode],
  )

  /** Only real work counts as typing — focus/blur must not dim the log. */
  const handleTelemetry = useCallback(
    (item: TelemetryItem) => {
      if (item.type === 'EDIT' || item.type === 'PASTE') {
        mark()
        lastActivityRef.current = Date.now()
        touchedFilesRef.current.add(item.file)

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

  /** One file names itself; a few name themselves; more collapses to a count. */
  const turnFileLabel = useCallback(() => {
    const touched = [...touchedFilesRef.current]
    touchedFilesRef.current = new Set()
    if (touched.length === 0) {
      return files[0]?.name ?? ''
    }
    if (touched.length <= MAX_NAMED_FILES_IN_LABEL) {
      return touched.join(', ')
    }
    return `${touched.length} files`
  }, [files])

  const begin = useCallback(async () => {
    setStarting(true)
    setError(null)
    try {
      const started = await startSession({ difficulty, problemType })
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
      touchedFilesRef.current = new Set()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setStarting(false)
    }
  }, [difficulty, problemType])

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
      // Read (and drain) the touched-file set out here, never inside the
      // updater below: React calls an updater during render, twice under
      // StrictMode, so the second call would find the set already emptied and
      // label every turn with the first file in the problem.
      const file = turnFileLabel()

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
    [turnFileLabel],
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

  /**
   * Hand the turn back: close it, then let them judge what you handed over.
   * There is nothing to run locally anymore (CLAUDE.md §6) — the interviewer
   * judges the current diff against the rubric, same as every other reaction.
   */
  const submit = useCallback(async () => {
    if (!sessionId || running || finishing) return
    setRunning(true)
    setError(null)
    closeTurn(false, null)
    try {
      await submitTurn(sessionId)
    } catch (e) {
      setError(
        e instanceof Error
          ? `Could not submit this turn. Your work is still here; try again. ${e.message}`
          : 'Could not submit this turn. Your work is still here; try again.',
      )
    } finally {
      setRunning(false)
    }
  }, [closeTurn, finishing, running, sessionId])

  const end = useCallback(async () => {
    if (!sessionId || finishing) return
    setFinishing(true)
    setError(null)
    try {
      // The last batch has to land before the report is written, or the
      // interviewer grades a buffer up to 1.5s stale — and it has to be the
      // last one, because /finish drops the session from the live map and
      // anything sent after it 404s.
      await flush()
      stop()
      const result = await finishSession(sessionId)
      if (!result.report) {
        throw new Error('The report did not arrive.')
      }
      setReport(result.report)
      setSession(null)
      setStartedAt(null)
      startedAtRef.current = null
      setArmed(false)
    } catch (e) {
      const detail = e instanceof Error && e.message ? ` ${e.message}` : ''
      setError(`Could not finish the session. Your work is still open; try ending again.${detail}`)
    } finally {
      setFinishing(false)
    }
  }, [finishing, flush, sessionId, stop])

  /** Mouse and keyboard both require a deliberate second end action. */
  const requestEnd = useCallback(() => {
    if (!sessionId || finishing) return
    if (armed) {
      setArmed(false)
      void end()
      return
    }
    setArmed(true)
  }, [armed, end, finishing, sessionId])

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
      requestEnd()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [requestEnd, sessionId])

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
          <DifficultyPicker value={difficulty} onChange={setDifficulty} disabled={starting} />
          <ProblemTypePicker value={problemType} onChange={setProblemType} disabled={starting} />

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
        type={session.problem.type}
        entries={entries}
        incoming={composing}
        connected={stream.connected}
      />

      <div className="shrink-0">
        {error && (
          <p role="alert" className="mx-auto w-full max-w-[84ch] px-6 pb-2 text-xs text-hot">
            <span aria-hidden>⎿ </span>
            {error}
          </p>
        )}
        <LiveTurn
          files={files}
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
          onEnd={requestEnd}
        />
      </div>
    </div>
  )
}
