import type { Utterance } from '../api/types'

interface Props {
  /** The problem. It asked you this, and it does not go away. */
  statement: string
  /** Only the most recent line is ever shown — see below. */
  utterance: Utterance | null
  connected: boolean
}

/**
 * The interviewer, speaking from above (UI-DESIGN.md §4.1).
 *
 * <p>Deliberately not a chat log. A transcript makes this a messaging app; a
 * single line that fades and is replaced makes it a person talking. History
 * belongs in the report card, not here.
 */
export function VoiceBand({ statement, utterance, connected }: Props) {
  return (
    <header className="shrink-0 px-8 pt-12 pb-8">
      <div className="mx-auto w-full max-w-[68ch]">
        <p className="dimmable-soft text-sm leading-relaxed text-sub transition-opacity duration-300">
          {statement}
        </p>

        <div className="mt-8 min-h-[4rem]" aria-live="polite">
          {utterance ? (
            <div
              key={utterance.id}
              className="animate-utterance-in flex items-start gap-4"
            >
              <span
                className="mt-1 w-[3px] shrink-0 self-stretch bg-accent"
                aria-hidden
              />
              <p className="flex-1 text-xl leading-relaxed text-ink">{utterance.line}</p>
              {utterance.canned && (
                <span className="mt-2 shrink-0 text-[9px] text-faint" title="fallback line, not the model">
                  canned
                </span>
              )}
            </div>
          ) : (
            <p className="text-sm text-faint">
              {connected ? 'they are watching' : 'connecting…'}
            </p>
          )}
        </div>
      </div>
    </header>
  )
}
