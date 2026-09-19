import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { MetaLine, type TurnStamp } from './MetaLine'
import { TypedText } from './TypedText'
import { TypingIndicator } from './TypingIndicator'
import type { ProblemType } from '../api/types'

/** A prompt from the human on the other side. */
export interface PromptEntry {
  kind: 'prompt'
  id: string
  line: string
  canned: boolean
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

/** A block: a marker in the left column, content hanging-indented beside it. */
export function Block({ marker, tone, children }: { marker: string; tone: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[1.25rem_1fr] gap-x-2">
      <span aria-hidden className={tone}>
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
      <Block marker="▌" tone={newest ? 'text-accent' : 'text-faint'}>
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
  /** Auto-scroll only while the candidate is already at the bottom. */
  const stuck = useRef(true)

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
        <div className="dimmable-soft sticky top-0 z-10 bg-canvas pt-8 pb-3 transition-opacity duration-300">
          <Block marker="▌" tone="text-faint">
            <TypedText
              text={statement}
              animate
              className="text-[15px] leading-relaxed text-ink"
            />
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
