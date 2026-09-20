import { useCallback, useEffect, useRef, useState } from 'react'
import { finishSession, requestHint, startSession } from './api/client'
import type {
  Difficulty,
  ProblemType,
  ProblemFile,
  ReportCard,
  SessionResponse,
  TelemetryItem,
  Utterance,
} from './api/types'
import { BootSequence } from './components/BootSequence'
import { HintPanel, type HintEntry } from './components/HintPanel'
import type { SessionLanguage } from './components/LanguagePicker'
import { Leaderboard } from './components/Leaderboard'
import { LiveTurn } from './components/LiveTurn'
import { BrandLanding } from './components/BrandLanding'
import type { TurnStamp } from './components/MetaLine'
import { ReportView } from './components/ReportView'
import { StatusLine } from './components/StatusLine'
import { Transcript, type Entry, type PromptEntry } from './components/Transcript'
import { WindowTab } from './components/WindowTab'
import { useIdentity } from './hooks/useIdentity'
import { useSessionStream } from './hooks/useSessionStream'
import { useSpeech } from './hooks/useSpeech'
import { useTelemetry } from './hooks/useTelemetry'
import { useTypingFocus } from './hooks/useTypingFocus'
import { loadRating, saveRating } from './lib/rating'

/** Mirrors `SessionState.MAX_HINTS` on the backend. */
const MAX_HINTS = 2

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

/**
 * The longest that beat stretches while a line's audio is still being made.
 *
 * <p>Synthesis measured one to three seconds, which is slower than the 850ms
 * above, so the caret waits for the clip rather than letting the voice start
 * two seconds into a reveal that lasts 2.8. The wait is capped because a
 * failing key or a slow night must not hold the log open indefinitely — past
 * this the line lands and is simply read. Muted, or with no audio at all, the
 * floor is all that applies and nothing here is reached.
 */
const MAX_COMPOSING_MS = 3000

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
  const [error, setError] = useState<string | null>(null)
  const [entries, setEntries] = useState<Entry[]>([])
  const [armed, setArmed] = useState(false)
  /** Their choice, sent with the session. Medium is the honest default. */
  const [difficulty, setDifficulty] = useState<Difficulty>('medium')
  const [language, setLanguage] = useState<SessionLanguage>('javascript')
  const [problemType, setProblemType] = useState<ProblemType>('BUILD')
  const [report, setReport] = useState<ReportCard | null>(null)
  const [finishing, setFinishing] = useState(false)
  /**
   * The anonymous rating: every session *this browser* has finished, with no
   * handle behind it (lib/rating.ts). Signed in, the server's number wins —
   * see `rating` below.
   */
  const [localRating, setLocalRating] = useState<number>(() => loadRating())
  /** Who is playing, or nobody. Owns the claim/resume/sign-out dance. */
  const identity = useIdentity()
  const { adopt } = identity
  /** The board, which is a screen rather than a panel — UI-DESIGN.md §2. */
  const [board, setBoard] = useState(false)

  /**
   * One number for the whole app. A signed-in candidate reads their standing
   * off the server, because that is what the board ranks; a signed-out one
   * reads this browser's total, which is what the app did before handles
   * existed and still does for anyone who does not want one.
   */
  const rating = identity.profile?.rating ?? localRating
  const handle = identity.profile?.handle

  /** The prelude is on screen (§4.8a). */
  const [booting, setBooting] = useState(false)
  /** Its script has run out. */
  const [bootDone, setBootDone] = useState(false)
  /** The session, fetched while the prelude played, waiting to be handed over. */
  const [pending, setPending] = useState<SessionResponse | null>(null)

  /** Utterances waiting behind their typing indicator. */
  const [queue, setQueue] = useState<Utterance[]>([])
  const [composing, setComposing] = useState(false)

  /** Hints received this session, in their own box — never queued into `entries`. */
  const [hints, setHints] = useState<HintEntry[]>([])
  const [requestingHint, setRequestingHint] = useState(false)
  const hintsRemaining = MAX_HINTS - hints.length

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
  /** The interviewer, out loud. Silent when there is no key or they muted it. */
  const speech = useSpeech()
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

  /** Everything a fresh session has to zero. Runs when the prelude hands over. */
  const enter = useCallback(
    (started: SessionResponse) => {
      // Anything left over from the last session's verdict, before the new
      // problem is read out over the top of it.
      speech.stop()
      setSession(started)
      setReport(null)
      const now = Date.now()
      setStartedAt(now)
      startedAtRef.current = now
      setElapsed(0)
      setEntries([])
      setQueue([])
      setComposing(false)
      setHints([])
      setRequestingHint(false)
      setTotals(ZERO)
      totalsRef.current = ZERO
      setTurnBase(TURN_ZERO)
      turnBaseRef.current = TURN_ZERO
      lastActivityRef.current = now
      seenRef.current = new Set()
      seenNotesRef.current = 0
      turnSeqRef.current = 0
      touchedFilesRef.current = new Set()

      // The problem, read out under the pinned prompt as it types itself.
      //
      // Deliberately *not* waited on the way a heckle is. A paragraph takes
      // about six seconds to synthesise against a 2.8s reveal, so nothing
      // short of stalling the begin button would line them up, and the trade
      // is the wrong way round: a heckle is a punchline and needs its timing,
      // while the statement stays pinned for the whole session and is no worse
      // for being read aloud a beat after it appears. That is also what a room
      // sounds like — they write the problem, then read it to you.
      void speech.say(started.statementSpeechId)
    },
    [speech],
  )

  const begin = useCallback(async () => {
    // Inside the click, before the await: a browser only grants a page the
    // right to make noise from a real user gesture, and the grant attaches to
    // the element played on. Miss this and the first heckle is swallowed.
    speech.prime()
    setError(null)
    setStarting(true)
    setBoard(false)
    setBooting(true)
    setBootDone(false)
    setPending(null)
    try {
      setPending(
        await startSession({ difficulty, language, problemType, identity: identity.identity }),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBooting(false)
    } finally {
      setStarting(false)
    }
  }, [difficulty, identity.identity, language, problemType, speech])

  const handleBootDone = useCallback(() => setBootDone(true), [])

  /**
   * The prelude and the session request run together, and whichever finishes
   * last is what the candidate waits on. Neither hands over alone: the animation
   * with no problem behind it is a dead screen, and a problem arriving without
   * it snaps straight past the premise.
   */
  useEffect(() => {
    if (!booting || !bootDone || !pending) return
    enter(pending)
    setPending(null)
    setBooting(false)
  }, [booting, bootDone, enter, pending])

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
    let cancelled = false
    const [next, ...rest] = queue

    // The beat ends when the caret has blinked its minimum *and* the voice is
    // ready — or when the ceiling runs out, whichever comes first. Fetching
    // starts now rather than at landing, so the wait is the remainder of a
    // synthesis already in flight server-side, not a fresh round trip.
    const floor = new Promise<void>((resolve) => window.setTimeout(resolve, COMPOSING_MS))
    const ceiling = new Promise<void>((resolve) => window.setTimeout(resolve, MAX_COMPOSING_MS))
    const audible = speech.load(next.id).then((ready) => {
      if (!ready) throw new Error('silent')
    })

    void Promise.all([floor, Promise.race([audible.catch(() => {}), ceiling])]).then(() => {
      if (cancelled) return
      const idleSeconds = IDLE_TRIGGERS.has(next.trigger)
        ? Math.round((Date.now() - lastActivityRef.current) / 1000)
        : null

      closeTurn(typingRef.current, idleSeconds)
      // Played as the line lands, never during the caret: that beat is the one
      // moment they know something is coming and can do nothing about it
      // (UI-DESIGN.md §4.4), and a voice starting inside it spends the dread.
      speech.play()
      setEntries((previous) =>
        [
          ...previous,
          {
            kind: 'prompt' as const,
            id: next.id,
            line: next.line,
            canned: next.canned,
            mood: next.mood,
            notes: [],
          },
        ].slice(-MAX_ENTRIES),
      )
      setQueue(rest)
    })

    // Same contract as the timer this replaced: a second prompt arriving
    // mid-beat re-runs the effect and re-arms it for the one already waiting,
    // rather than cancelling it. Guarding on `composing` deadlocks it.
    return () => {
      cancelled = true
    }
  }, [closeTurn, queue, speech])

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
   * Asks for a hint directly. Deliberately not a turn boundary and not routed
   * through the trigger engine — it does not close the live turn, does not
   * touch the diff baseline, and never becomes an entry in the transcript
   * (§4.3a's file-switch is the closest precedent: a view change, not a new
   * turn). It lands in its own list, rendered by HintPanel, never interleaved
   * with the criticism log.
   */
  const handleHint = useCallback(async () => {
    if (!sessionId || requestingHint || hintsRemaining <= 0) return
    setRequestingHint(true)
    setError(null)
    try {
      const result = await requestHint(sessionId)
      setHints((previous) => [...previous, { text: result.text, canned: result.canned }])
    } catch (e) {
      setError(e instanceof Error ? `Could not get a hint. ${e.message}` : 'Could not get a hint.')
    } finally {
      setRequestingHint(false)
    }
  }, [hintsRemaining, requestingHint, sessionId])

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
      const { ratingDelta } = result.report
      setReport(result.report)

      // Cut off any heckle still playing before the verdict starts: two
      // voices over each other is the one thing worse than silence. The
      // clip has been synthesising since the end of /finish, so it lands
      // while the verdict is still typing itself out.
      speech.stop()
      void speech.say(result.report.speechId)

      // Applied right here, synchronously with the report landing — not in an
      // effect — so the very first paint of the report screen's WindowTab
      // already shows the updated total, not last session's.
      //
      // Signed in, the server has already done the arithmetic and
      // `result.user` is the answer; adding the delta again here would double
      // it. Signed out there is nobody to do it, so the browser keeps its own
      // total the way it always has.
      if (result.user) {
        adopt(result.user)
      } else {
        setLocalRating((current) => {
          const next = current + ratingDelta
          saveRating(next)
          return next
        })
      }
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
    // `adopt` rather than the whole `identity` object: the hook returns a
    // fresh literal every render, and App re-renders on every keystroke — a
    // dependency on it would rebuild `end`, then `requestSubmit`, then re-bind
    // the ^d listener, once per character typed.
  }, [adopt, finishing, flush, sessionId, speech, stop])

  /**
   * The footer's `submit`, which is also the end of the session — there is no
   * per-turn submit any more, so handing the work in and finishing are one
   * action. Mouse and keyboard both require a deliberate second press.
   */
  const requestSubmit = useCallback(() => {
    if (!sessionId || finishing) return
    if (armed) {
      setArmed(false)
      void end()
      return
    }
    setArmed(true)
  }, [armed, end, finishing, sessionId])

  /**
   * `^d` submits, on the second press — the terminal's own way out,
   * and it leaves `esc` alone, which in this layout is theirs (§4.5). `^c`
   * would have been more idiomatic still, but it is copy, and a candidate
   * copying a line should not end their interview.
   */
  useEffect(() => {
    if (!sessionId) return
    const handler = (event: KeyboardEvent) => {
      if (!event.ctrlKey) return
      // `^m` mutes. Same shape as `^d`: a control chord, not a key that
      // belongs to the editor, and it takes effect on the first press because
      // unlike ending a session there is nothing to confirm.
      if (event.key.toLowerCase() === 'm') {
        event.preventDefault()
        speech.toggleMuted()
        return
      }
      if (event.key.toLowerCase() !== 'd') return
      event.preventDefault()
      requestSubmit()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [requestSubmit, sessionId, speech])

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

  if (report) {
    return (
      <ReportView
        report={report}
        rating={rating}
        handle={handle}
        profile={identity.profile}
        onRestart={() => setReport(null)}
        onLeaderboard={() => {
          setReport(null)
          setBoard(true)
        }}
      />
    )
  }

  // Checked before `session` and after `report`: the board is reachable from
  // the start screen and from the report card, and from nowhere during an
  // interview — leaving the editor to go and look at a scoreboard is exactly
  // the practice-site move UI-DESIGN.md §2 is about.
  if (board) {
    return (
      <Leaderboard
        profile={identity.profile}
        rating={rating}
        handle={handle}
        onBack={() => setBoard(false)}
        onBegin={begin}
      />
    )
  }

  if (booting) {
    return (
      <BootSequence
        ready={pending !== null}
        onDone={handleBootDone}
        rating={rating}
        handle={handle}
      />
    )
  }

  if (!session) {
    return (
      <BrandLanding
        difficulty={difficulty}
        language={language}
        problemType={problemType}
        starting={starting}
        error={error}
        identityStatus={identity.status}
        profile={identity.profile}
        identityError={identity.error}
        onDifficultyChange={setDifficulty}
        onLanguageChange={setLanguage}
        onProblemTypeChange={setProblemType}
        onClaim={identity.claim}
        onSignOut={identity.signOut}
        onLeaderboard={() => setBoard(true)}
        onBegin={begin}
      />
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
      {/* Not dimmable. It is the window, not the session — §7 recedes what you
          produced and what it cost, never the frame around it. */}
      <WindowTab status="coding" rating={rating} handle={handle} />

      {/* Two columns: the human's side of the glass on the left — what they
          asked for and everything they have said about it since — and your
          output on the right, full height, since it is no longer sharing a
          column with the scrolled-back log. No divider: zones are told apart
          by whitespace, the same rule that already kept this app border-free. */}
      <div className="flex min-h-0 flex-1">
        <div className="flex min-h-0 min-w-0 flex-[5] flex-col">
          <Transcript
            statement={session.problem.statement}
            type={session.problem.type}
            entries={entries}
            incoming={composing}
            connected={stream.connected}
          />

          <div className="shrink-0">
            <HintPanel hints={hints} />
            {error && (
              <p role="alert" className="w-full px-6 pb-2 text-xs text-hot">
                <span aria-hidden>└ </span>
                {error}
              </p>
            )}
            <StatusLine
              elapsedSeconds={elapsed}
              totals={totals}
              impatience={stream.impatience}
              activity={finishing ? 'submitting' : typing ? 'writing' : 'idle'}
              finishing={finishing}
              armed={armed}
              hintsRemaining={hintsRemaining}
              requestingHint={requestingHint}
              muted={speech.muted}
              onToggleMuted={speech.toggleMuted}
              onSubmit={requestSubmit}
              onHint={handleHint}
            />
          </div>
        </div>

        <div className="flex min-h-0 min-w-0 flex-[6] flex-col">
          <LiveTurn
            files={files}
            stamp={liveStamp}
            onTelemetry={handleTelemetry}
            onCodeChange={handleCodeChange}
          />
        </div>
      </div>
    </div>
  )
}
