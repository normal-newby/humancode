import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { finishSession, sendRunResult, startSession } from './api/client'
import type { SessionResponse, TelemetryItem } from './api/types'
import { EditorPane } from './components/EditorPane'
import { LeftRail } from './components/LeftRail'
import { NotesPanel } from './components/NotesPanel'
import { VoiceBand } from './components/VoiceBand'
import { useSessionStream } from './hooks/useSessionStream'
import { useTelemetry } from './hooks/useTelemetry'
import { useTypingFocus } from './hooks/useTypingFocus'
import { runTests, type LocalRunResult } from './lib/runTests'

export default function App() {
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [starting, setStarting] = useState(false)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastRun, setLastRun] = useState<LocalRunResult | null>(null)
  /** Current editor contents, for the test runner. */
  const codeRef = useRef('')

  const sessionId = session?.sessionId ?? null
  const stream = useSessionStream(sessionId)
  const { record, setCode, metrics } = useTelemetry(sessionId)

  const handleCodeChange = useCallback(
    (code: string) => {
      codeRef.current = code
      setCode(code)
    },
    [setCode],
  )
  const { typing, mark } = useTypingFocus()

  /** Only real work counts as typing — focus/blur must not dim the rails. */
  const handleTelemetry = useCallback(
    (item: TelemetryItem) => {
      if (item.type === 'EDIT' || item.type === 'PASTE') {
        mark()
      }
      record(item)
    },
    [mark, record],
  )

  const begin = useCallback(async () => {
    setStarting(true)
    setError(null)
    try {
      setSession(await startSession({}))
      setStartedAt(Date.now())
      setElapsed(0)
      setLastRun(null)
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

  const run = useCallback(async () => {
    if (!sessionId || !session) return
    setRunning(true)
    try {
      // Executed in a throwaway Web Worker, with a hard timeout — see
      // lib/runTests.ts. The verdict below is the real one.
      const result = await runTests(session.problem, codeRef.current)
      setLastRun(result)

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
  }, [session, sessionId])

  const end = useCallback(async () => {
    if (!sessionId) return
    try {
      await finishSession(sessionId)
    } finally {
      setSession(null)
      setStartedAt(null)
    }
  }, [sessionId])

  useEffect(() => {
    if (!sessionId) return
    const handler = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [sessionId])

  const latest = useMemo(
    () => (stream.utterances.length ? stream.utterances[stream.utterances.length - 1] : null),
    [stream.utterances],
  )

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

  return (
    <div data-typing={typing} className="flex h-screen flex-col bg-canvas text-ink">
      <VoiceBand
        statement={session.problem.statement}
        utterance={latest}
        connected={stream.connected}
      />

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-8 px-8 pb-8 wide:grid-cols-[13.5rem_1fr_16.5rem]">
        <LeftRail
          impatience={stream.impatience}
          elapsedSeconds={elapsed}
          metrics={metrics}
          problem={session.problem}
          running={running}
          lastRun={lastRun}
          onRun={run}
          onEnd={end}
        />

        <main className="flex min-h-0 flex-col">
          <EditorPane
            language={session.language}
            initialCode={session.problem.starterCode}
            onTelemetry={handleTelemetry}
            onCodeChange={handleCodeChange}
          />
        </main>

        {/* Below the wide breakpoint the notes collapse to a native disclosure
            rather than occupying a column that no longer exists. */}
        <div className="hidden min-h-0 wide:block">
          <NotesPanel notes={stream.notes} />
        </div>
        <details className="shrink-0 wide:hidden">
          <summary className="cursor-pointer text-xs lowercase text-sub">
            private notes{' '}
            <span className="text-faint">
              {stream.notes.length ? `(${stream.notes.length})` : '(none)'}
            </span>
          </summary>
          <div className="mt-3 max-h-40 overflow-y-auto">
            <NotesPanel notes={stream.notes} />
          </div>
        </details>
      </div>
    </div>
  )
}
