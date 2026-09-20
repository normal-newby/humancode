export interface HintEntry {
  text: string
  canned: boolean
}

interface Props {
  hints: HintEntry[]
}

/**
 * Hints, in a box of their own.
 *
 * <p>Deliberately not part of the Transcript's log: a hint is not a reaction
 * to anything you typed, it is something you asked for directly, and mixing
 * it into the criticism feed would read as the interviewer volunteering help
 * it does not volunteer. Numbered rather than bordered or boxed, in the app's
 * own vocabulary — zones are separated by whitespace and contrast, not a
 * panel (UI-DESIGN.md §2).
 */
export function HintPanel({ hints }: Props) {
  if (hints.length === 0) return null

  return (
    <div className="px-6 pt-1 pb-3">
      <p className="text-[11px] lowercase text-faint">hints</p>
      {hints.map((hint, index) => (
        <div key={index} className="mt-1.5 flex items-baseline gap-2 text-[13px] leading-relaxed">
          <span aria-hidden className="shrink-0 text-faint">
            {index + 1}.
          </span>
          <span className="min-w-0 break-words text-sub">{hint.text}</span>
          {hint.canned && (
            <span className="shrink-0 text-[9px] text-faint" title="fallback hint, not the model">
              canned
            </span>
          )}
        </div>
      ))}
    </div>
  )
}
