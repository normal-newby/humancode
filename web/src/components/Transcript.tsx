import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { MetaLine, type TurnStamp } from './MetaLine'
import { TypedText } from './TypedText'
import { TypingIndicator } from './TypingIndicator'
import { PixelFace } from './PixelFace'
import type { Mood, ProblemType } from '../api/types'

/** A prompt from the human on the other side. */
export interface PromptEntry {
  kind: 'prompt'
  id: string
  line: string
  canned: boolean
  /** The face they were wearing when they sent it (§6a). */
  mood: Mood
  /** What they jotted down while you worked. */
  notes: string[]
}

/** One of your turns, closed by a submit or cut short by their prompt. */
export interface TurnEntry {
  kind: 'turn'
  id: string
  /** `twoSum.js` — the tool call your typing amounts to. */
  file: string
  stamp: TurnStamp
  interrupted: boolean
  /** Nothing was typed during it. */
  empty: boolean
}

export type Entry = PromptEntry | TurnEntry

interface Props {
  statement: string
  type: ProblemType
  entries: Entry[]
  /** Their caret, blinking before a prompt lands. */
  incoming: boolean
  connected: boolean
}

/**
 * A block: a marker in the left column, content hanging-indented beside it.
 *
 * <p>`marker` is a node rather than a character because one of them is a face
 * (§6a). It still occupies the same 1.25rem column as every `▌` and `•`, so
 * the log stays aligned all the way down and the face reads as the marker for
 * that block rather than as an avatar bolted onto it.
 */
export function Block({
  marker,
  tone,
  children,
}: {
  marker: ReactNode
  tone: string
  children: ReactNode
}) {
  return (
    <div className="grid grid-cols-[1.25rem_1fr] gap-x-2">
      <span aria-hidden={typeof marker === 'string'} className={tone}>
        {marker}
      </span>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

/** A dim continuation line under a block, the way codex hangs tool output. */
export function Result({ tone = 'text-sub', children }: { tone?: string; children: ReactNode }) {
  return (
    <div className={`mt-1 flex items-baseline gap-2 text-xs ${tone}`}>
      <span aria-hidden className="text-faint">
        └
      </span>
      <span className="min-w-0 break-words">{children}</span>
    </div>
  )
}

/**
 * A prompt from the human. Types itself out if it arrived while mounted; their
 * notes hang under it as asides, because that is where a real interviewer's
 * scribbling belongs — in their message, not in a panel of their own.
 */
function Prompt({ entry, newest, onTick }: { entry: PromptEntry; newest: boolean; onTick: () => void }) {
  const [animate] = useState(newest)
  const [done, setDone] = useState(!animate)
  const handleDone = useCallback(() => setDone(true), [])

  return (
    <div className={newest ? 'animate-turn-in' : 'dimmable'}>
      {/* Their face is the marker while the line is theirs to answer for; once
          it is old news it recedes to `--color-faint` the way the `▌` it
          replaced always did. */}
      <Block
        marker={
          <PixelFace
            mood={entry.mood}
            className="mt-[0.2rem] h-4 w-4"
            tone={newest ? undefined : 'text-faint'}
            animate={newest}
          />
        }
        tone={newest ? 'text-accent' : 'text-faint'}
      >
        <TypedText
          text={entry.line}
          animate={animate}
          onTick={onTick}
          onDone={handleDone}
          className={`text-[15px] leading-relaxed transition-colors duration-[400ms] ${
            newest ? 'text-ink' : 'text-faint'
          }`}
        />
        {done &&
          entry.notes.map((note, index) => (
            <div key={index} className="animate-meta-in">
              <Result tone="text-faint">
                <span className="lowercase">{note}</span>
              </Result>
            </div>
          ))}
        {done && entry.canned && (
          <span className="mt-1 block text-[9px] text-faint" title="fallback line, not the model">
            canned
          </span>
        )}
      </Block>
    </div>
  )
}

/** One of your closed turns, rendered as the tool call it amounts to. */
function Turn({ entry }: { entry: TurnEntry }) {
  return (
    <div className="dimmable">
      <Block marker="•" tone="text-faint">
        <p className="text-[15px] leading-relaxed text-faint">
          {entry.empty ? '(no output)' : `Edited ${entry.file}`}
        </p>
        <MetaLine stamp={entry.stamp} />
        {entry.interrupted && <Result tone="text-hot">Interrupted by user</Result>}
      </Block>
    </div>
  )
}

/**
 * The log (UI-DESIGN.md §4.2).
 *
 * <p>Read it as a codex session with the roles swapped: `▌` lines are the human
 * prompting, `•` blocks are your output. Their standing prompt is pinned at the
 * top; everything since scrolls under it.
 */
export function Transcript({ statement, type, entries, incoming, connected }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const endRef = useRef<HTMLDivElement>(null)
  const statementRef = useRef<HTMLDivElement>(null)
  /** Auto-scroll only while the candidate is already at the bottom. */
  const stuck = useRef(true)

  /**
   * The pinned prompt is clamped once there is anything under it.
   *
   * <p>It is sticky inside a scroll container that is only about half the
   * screen, so a problem statement of any length simply *is* the log — their
   * heckles land in a strip with no room left to show it. Pinned still means
   * pinned (§4.2): the opening lines never leave, and `more` puts the rest
   * back. It stays open while it is being delivered, because that beat is the
   * problem being set, and folds itself away the moment the first block lands.
   */
  const [collapsed, setCollapsed] = useState(false)
  const [clipped, setClipped] = useState(false)
  /** The statement has finished typing itself out. */
  const [delivered, setDelivered] = useState(false)
  /** Once they have chosen for themselves, stop choosing for them. */
  const chosen = useRef(false)
  const started = entries.length > 0 || incoming

  const handleDelivered = useCallback(() => setDelivered(true), [])

  // Never mid-delivery: their first heckle can land while the problem is still
  // typing itself out, and folding it then means the candidate never reads the
  // half they were not shown.
  useEffect(() => {
    if (!started || !delivered || chosen.current) return
    setCollapsed(true)
  }, [delivered, started])

  const toggle = useCallback(() => {
    chosen.current = true
    setCollapsed((previous) => !previous)
  }, [])

  // Whether `more` would show anything. Measured rather than guessed from the
  // statement's length: what clips depends on the wrap, which depends on the
  // window.
  useLayoutEffect(() => {
    // The clamp lives on the <p> TypedText renders, and it is the thing whose
    // overflow is hidden — the wrapper around it reports no overflow at all
    // and would say nothing is ever clipped.
    const element = statementRef.current?.firstElementChild
    if (!element || !collapsed) return
    const measure = () => setClipped(element.scrollHeight - element.clientHeight > 1)
    measure()
    // What clips depends on the wrap, so a resized window can silently make
    // the toggle a lie in either direction.
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [collapsed, delivered, statement, entries.length])

  const follow = useCallback((smooth = false) => {
    if (!stuck.current) return
    endRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'end' })
  }, [])

  useEffect(() => {
    follow(true)
  }, [entries.length, incoming, follow])

  const onScroll = () => {
    const element = scrollRef.current
    if (!element) return
    stuck.current = element.scrollHeight - element.scrollTop - element.clientHeight < 48
  }

  const lastPromptId = [...entries].reverse().find((entry) => entry.kind === 'prompt')?.id

  return (
    <div ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-[84ch] px-6">
        {/* Their standing prompt. It is what you are still being asked. */}
        {/* Once it is folded the session is under way and the top gap is just
            space the log does not have — a short window leaves the whole scroll
            container barely taller than this block. */}
        <div
          className={`dimmable-soft sticky top-0 z-10 bg-canvas pb-3 transition-opacity duration-300 ${
            collapsed ? 'pt-4' : 'pt-8'
          }`}
        >
          {/* They have not seen a line of your code yet, so the opening face is
              the one they set the problem with. */}
          <Block
            marker={<PixelFace mood="NEUTRAL" className="mt-[0.2rem] h-4 w-4" tone="text-faint" />}
            tone="text-faint"
          >
            <div ref={statementRef} className={collapsed ? 'overflow-hidden' : undefined}>
              {/* The clamp is spelled out, not built from COLLAPSED_LINES:
                  Tailwind scans source text, and a class it never sees written
                  is a class it never generates. */}
              <TypedText
                text={statement}
                animate
                onDone={handleDelivered}
                className={`text-[15px] leading-relaxed text-ink ${collapsed ? 'line-clamp-3' : ''}`}
              />
            </div>
            {(collapsed ? clipped : started && delivered) && (
              <button
                type="button"
                onClick={toggle}
                aria-expanded={!collapsed}
                className="mt-1 flex items-baseline gap-2 text-xs lowercase text-faint transition-colors hover:text-sub"
              >
                <span aria-hidden>└</span>
                <span>{collapsed ? 'the rest of it' : 'fold it away'}</span>
              </button>
            )}
          </Block>
          {type === 'BUG_FIX' && (
            <Result tone="text-hot">bug hunt: the app is already written. find what breaks.</Result>
          )}
          <div
            aria-hidden
            className="mt-3 overflow-hidden text-xs whitespace-nowrap text-faint select-none"
          >
            ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
          </div>
        </div>

        <div className="space-y-5 pt-5 pb-4" aria-live="polite">
          {entries.length === 0 && !incoming && (
            <p className="text-xs text-faint">{connected ? 'they are watching' : 'connecting…'}</p>
          )}

          {entries.map((entry) =>
            entry.kind === 'prompt' ? (
              <Prompt
                key={entry.id}
                entry={entry}
                newest={entry.id === lastPromptId}
                onTick={follow}
              />
            ) : (
              <Turn key={entry.id} entry={entry} />
            ),
          )}

          {incoming && <TypingIndicator />}
          <div ref={endRef} />
        </div>
      </div>
    </div>
  )
}
