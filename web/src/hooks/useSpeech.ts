import { useCallback, useEffect, useRef, useState } from 'react'
import { speechUrl } from '../api/client'

const STORAGE_KEY = 'gpdetox.muted'

/**
 * How long to wait for a clip before giving up on it.
 *
 * <p>This is a backstop, not pacing. Neither `canplay` nor `error` is
 * guaranteed to fire — a hidden tab defers media loading entirely and neither
 * ever arrives — and without a deadline the promise would dangle. What appears
 * on screen is bounded by the caller's own, much shorter cap
 * (`MAX_COMPOSING_MS`), so this only has to outlast the slowest clip: the
 * problem statement, a paragraph rather than a line, measured at 6.2 seconds.
 */
const LOAD_TIMEOUT_MS = 12000

/**
 * A silent WAV, small enough to inline: 44 bytes of header and no samples.
 * Played once on the gesture that starts a session so the element is allowed
 * to make noise later — see `prime`.
 */
const SILENCE =
  'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA='

function loadMuted(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    // Storage can throw (private browsing, disabled cookies). Default to
    // audible — the voice is the feature, and a silent demo looks broken.
    return false
  }
}

/**
 * The interviewer, out loud.
 *
 * <p>One `<audio>` element for the whole app rather than one per line, for a
 * reason that is not tidiness: browsers only let a page play sound after a
 * user gesture, and the permission attaches to the element you played on. The
 * element is created and unlocked on the click that starts the session
 * (`prime`), and every line afterwards reuses it. Creating a fresh `Audio()`
 * per utterance would hand each new one an unprimed element and the first
 * heckle would be silently swallowed.
 *
 * <p><b>Failing to play is not an error anywhere.</b> A 404 is the server's
 * normal way of saying this line has no clip — no key, synthesis failed, or it
 * did not arrive in time (see `SpeechController`) — and a rejected `play()` is
 * the browser exercising its autoplay policy. Both end with the candidate
 * reading the line instead of hearing it, which is the app as it was last week.
 */
export function useSpeech() {
  const [muted, setMuted] = useState<boolean>(loadMuted)
  const elementRef = useRef<HTMLAudioElement | null>(null)
  const mutedRef = useRef(muted)

  useEffect(() => {
    mutedRef.current = muted
  }, [muted])

  /**
   * Called from the click that begins a session — a real user gesture, which
   * is the only thing that buys the page the right to make noise later.
   * Playing a zero-length source is the standard way to spend that gesture on
   * an element you intend to keep.
   */
  const prime = useCallback(() => {
    if (elementRef.current) return
    const audio = new Audio()
    audio.preload = 'auto'
    elementRef.current = audio
    // A real source, not an empty element. Chrome unlocks per document once
    // the page has been interacted with, so an empty play() would do — but
    // Safari unlocks per element and needs a play() that actually succeeded,
    // and play() on a src-less element rejects. A few bytes of silence is the
    // cheapest thing that genuinely plays.
    audio.src = SILENCE
    void audio
      .play()
      .then(() => audio.pause())
      .catch(() => {})
  }, [])

  /**
   * Fetches a line's clip and resolves once it is playable — or once it is
   * clear it never will be.
   *
   * <p>This exists because synthesis is slower than the app's own pacing. A v3
   * line measured between one and three seconds, while the caret that precedes
   * a prompt blinks for 850ms (UI-DESIGN.md §4.4), so a fire-and-forget play
   * starts the voice about two seconds into a reveal that lasts 2.8 — the
   * interviewer finishes typing and only then starts shouting.
   *
   * <p>The caller waits on this before landing the line, which turns the
   * latency into the one thing on that screen it can usefully become: a longer
   * beat of knowing something is coming. Never resolves rejected — a clip that
   * is not going to arrive resolves `false` and the line is simply read.
   */
  const load = useCallback((speechId: string | null | undefined): Promise<boolean> => {
    if (!speechId || mutedRef.current) return Promise.resolve(false)
    const audio = elementRef.current ?? new Audio()
    elementRef.current = audio

    return new Promise<boolean>((resolve) => {
      let settled = false
      const done = (ok: boolean) => {
        if (settled) return
        settled = true
        window.clearTimeout(deadline)
        audio.removeEventListener('canplay', onReady)
        audio.removeEventListener('error', onFail)
        resolve(ok)
      }
      const onReady = () => done(true)
      const onFail = () => done(false)

      // `canplay`, not `canplaythrough`: the latter waits for enough buffer to
      // play the clip end to end without stalling, which on a few seconds of
      // mp3 is the whole file. These are short and served from memory, so the
      // earlier event is both sufficient and noticeably sooner.
      audio.addEventListener('canplay', onReady, { once: true })
      // A 404 is the server's normal way of saying this line is silent, and it
      // surfaces here as an error event on the element.
      audio.addEventListener('error', onFail, { once: true })

      // Neither event is guaranteed. A hidden tab defers media loading
      // entirely — readyState sits at 0 and nothing ever fires, which was
      // observed — so without this the promise dangles and the caller leans on
      // its own cap. It should not have to: a promise that never settles is
      // the caller's bug to hit and this hook's to prevent.
      const deadline = window.setTimeout(() => done(false), LOAD_TIMEOUT_MS)

      audio.src = speechUrl(speechId)
      audio.load()
    })
  }, [])

  /** Plays whatever {@link load} last fetched. */
  const play = useCallback(() => {
    const audio = elementRef.current
    if (!audio || mutedRef.current || !audio.src) return
    audio.currentTime = 0
    void audio.play().catch(() => {})
  }, [])

  /** Load and play in one go, for a line nothing is waiting on. */
  const say = useCallback(
    async (speechId: string | null | undefined) => {
      if (await load(speechId)) play()
    },
    [load, play],
  )

  const stop = useCallback(() => {
    const audio = elementRef.current
    if (!audio) return
    audio.pause()
    // Dropping the source as well: pause alone leaves a half-played clip that
    // resumes from the middle if the same element is reused.
    audio.removeAttribute('src')
  }, [])

  const toggleMuted = useCallback(() => {
    setMuted((previous) => {
      const next = !previous
      try {
        window.localStorage.setItem(STORAGE_KEY, String(next))
      } catch {
        // Their choice just does not survive the tab. Not worth surfacing.
      }
      if (next) {
        const audio = elementRef.current
        audio?.pause()
      }
      return next
    })
  }, [])

  return { muted, toggleMuted, prime, load, play, say, stop }
}
