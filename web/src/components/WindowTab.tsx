/** Where the whole session pretends to be running. Shown beside the tab. */
export const CWD = '~/interviews/you'

interface Props {
  /**
   * What this screen is, in the register a terminal title carries — `coding`,
   * `session ended`. Omitted on the screens where the cwd says enough.
   */
  status?: string
}

/**
 * The window's one tab (UI-DESIGN.md §4.0).
 *
 * <p>Every screen in the app sits under it, which is the point: the start
 * screen, the prelude, the interview and the report card are not four pages,
 * they are one terminal that someone left open. It is the only piece of chrome
 * in the app and it is the only place the sponsor's name is spelled out.
 *
 * <p>It is a tab by fill alone — `--color-surface` against the canvas, flush
 * left, no border, no radius, no close affordance and nothing to click. §2
 * bans bordered cards and docked panels; a tab that is only a change of
 * background is inside that rule, and a second tab would not be, because then
 * it would be a tab bar and tab bars are what §2 is actually about.
 */
export function WindowTab({ status }: Props) {
  return (
    <div className="flex w-full shrink-0 items-center gap-3 pr-4 text-[11px] lowercase select-none">
      <span className="flex items-center gap-2 bg-surface px-3 py-1.5">
        <span aria-hidden className="text-accent">
          ▌
        </span>
        <span className="text-ink">openai codex</span>
      </span>
      <span className="truncate text-faint">
        {CWD}
        {status && ` — ${status}`}
      </span>
    </div>
  )
}
