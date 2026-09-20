import { useEffect, useRef, useState } from 'react'
import { Spinner } from './Spinner'
import { Block, Result } from './Transcript'
import { CWD, WindowTab } from './WindowTab'

/**
 * What they type at their shell to get you. Typed out here, because it is them
 * typing — and exported so the start screen's link can be the same string. A
 * link that says one thing and a prelude that types another breaks the one beat
 * this screen is for.
 */
export const BOOT_COMMAND = 'gpdetox'

/** ms per character of the command — brisk. They have done this before. */
const KEY_MS = 55
/** Beat between the command landing and the first line of output. */
const BOOT_MS = 220
/** Beat between boot lines. */
const LINE_MS = 240
/** Held after the last line, so the banner is readable before the problem lands. */
const SETTLE_MS = 420
/** Under reduced motion the whole thing is simply there, for a beat. */
const REDUCED_MS = 500

/**
 * The banner's own line, and then the session's settings as `└` receipts under
 * it — the shape codex prints on startup, and the same shape one of your closed
 * turns takes in the log.
 *
 * <p>The accented substring is the tell. Accent marks whoever is producing
 * output (UI-DESIGN.md §4.1), and on this screen that is about to be the
 * candidate.
 */
const HEADER = 'gpdetox cli  v1.0.0'

interface Receipt {
  text: string
  accent?: string
}

const RECEIPTS: Receipt[] = [
  { text: 'model: you  ·  approval: never asked', accent: 'you' },
  { text: `cwd: ${CWD}` },
  { text: '1 human connected. they want a feature built.' },
]

/** Every line of output, header included. */
const STEPS = RECEIPTS.length + 1

function usesReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** Splits a receipt so the one part that is about them can carry the accent. */
function render({ text, accent }: Receipt) {
  if (!accent) return text
  const at = text.indexOf(accent)
  if (at === -1) return text
  return (
    <>
      {text.slice(0, at)}
      <span className="text-accent">{accent}</span>
      {text.slice(at + accent.length)}
    </>
  )
}

interface Props {
  /** The session has arrived and the log can take over. */
  ready: boolean
  /** Every scripted line has landed. Fires once. */
  onDone: () => void
  /** Threaded straight through to the tab's own corner rating. */
  rating: number
  /** Ditto for whose rating it is. Absent when nobody is signed in. */
  handle?: string
}

/**
 * The prelude (UI-DESIGN.md §4.8a): the human on the other side invoking you.
 *
 * <p>It exists for two reasons. It states the premise in the only register this
 * app has — you are the thing that was just started, and something is about to
 * be asked of you — and it covers the session request, which is a pool hit on a
 * good day and a cold generation on a bad one. The script runs to its end
 * either way; if the problem is still coming when it finishes, a spinner takes
 * over rather than the screen sitting there finished and silent.
 *
 * <p>It sits at the log's own column and top offset, so the `$` here and the
 * pinned `▌` that replaces it land in the same place.
 */
export function BootSequence({ ready, onDone, rating, handle }: Props) {
  const reduced = useRef(usesReducedMotion()).current
  const [chars, setChars] = useState(reduced ? BOOT_COMMAND.length : 0)
  const [shown, setShown] = useState(reduced ? STEPS : 0)

  // Read through a ref so the timeline is built exactly once: rebuilding it when
  // the parent re-renders would restart the animation mid-boot.
  const done = useRef(onDone)
  useEffect(() => {
    done.current = onDone
  }, [onDone])

  useEffect(() => {
    const timers: number[] = []
    const at = (ms: number, run: () => void) => {
      timers.push(window.setTimeout(run, ms))
    }

    if (reduced) {
      at(REDUCED_MS, () => done.current())
    } else {
      for (let index = 1; index <= BOOT_COMMAND.length; index += 1) {
        at(index * KEY_MS, () => setChars(index))
      }
      const bootAt = BOOT_COMMAND.length * KEY_MS + BOOT_MS
      for (let index = 1; index <= STEPS; index += 1) {
        at(bootAt + index * LINE_MS, () => setShown(index))
      }
      at(bootAt + STEPS * LINE_MS + SETTLE_MS, () => done.current())
    }

    return () => timers.forEach(window.clearTimeout)
  }, [reduced])

  const typed = chars >= BOOT_COMMAND.length
  const waiting = shown >= STEPS && !ready

  return (
    <main className="flex min-h-screen flex-col bg-canvas">
      <WindowTab status="starting" rating={rating} handle={handle} />
      <div className="mx-auto w-full max-w-[84ch] px-6 pt-8">
        <Block marker="$" tone="text-faint">
          <p className="text-[15px] leading-relaxed text-ink">
            <span className="sr-only">the interviewer runs gpdetox</span>
            <span aria-hidden>
              {BOOT_COMMAND.slice(0, chars)}
              {/* Solid while it is being typed, blinking once it is not — their
                  caret always blinks, and yours never does (§4.4). */}
              <span
                className={`ml-0.5 inline-block translate-y-[0.1em] text-accent select-none ${
                  typed ? 'animate-blink' : ''
                }`}
              >
                ▍
              </span>
            </span>
          </p>
        </Block>

        <div className="mt-5" aria-live="polite">
          {shown > 0 && (
            <div className="animate-turn-in">
              <Block marker="•" tone="text-accent">
                <p className="text-[15px] leading-relaxed text-sub">{HEADER}</p>
                {RECEIPTS.slice(0, shown - 1).map((receipt) => (
                  <div key={receipt.text} className="animate-turn-in">
                    <Result tone="text-sub">{render(receipt)}</Result>
                  </div>
                ))}
              </Block>
            </div>
          )}
        </div>

        {waiting && (
          <div className="animate-turn-in mt-5 flex items-baseline gap-2 text-xs text-sub">
            <Spinner />
            <span>waiting for them to finish typing…</span>
          </div>
        )}
      </div>
    </main>
  )
}
