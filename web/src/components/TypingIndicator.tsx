/**
 * The human, composing (UI-DESIGN.md §4.4).
 *
 * <p>It appears a beat before their prompt lands, at the point in the log where
 * the prompt will land. Watching the caret blink and knowing something is
 * coming is worse than the line itself, which is the entire reason it exists.
 */
export function TypingIndicator() {
  return (
    <div className="grid grid-cols-[1.25rem_1fr] gap-x-2">
      <span aria-hidden className="text-faint">
        &gt;
      </span>
      <span aria-hidden className="animate-blink text-accent select-none">
        ▍
      </span>
      <span className="sr-only">the interviewer is typing</span>
    </div>
  )
}
