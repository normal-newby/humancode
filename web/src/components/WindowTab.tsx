/** Where the whole session pretends to be running. Shown beside the tab. */
export const CWD = '~/gpdetox/you'

interface Props {
  /**
   * What this screen is, in the register a terminal title carries — `coding`,
   * `session ended`. Omitted on the screens where the cwd says enough.
   */
  status?: string
  /**
   * The candidate's saved rating — the running total across every session
   * this browser has ever finished, not just the current one. Always shown,
   * on every screen, because it is meant to read as a persistent fact about
   * them rather than something that belongs to this session.
   */
  rating: number
}

/**
 * The window's one tab (UI-DESIGN.md §4.0).
 *
 * <p>Every screen in the app sits under it, which is the point: the start
 * screen, the prelude, the interview and the report card are not four pages,
 * they are one terminal that someone left open. It is the one persistent piece
 * of product identity.
 *
 * <p>It is a tab by fill alone — `--color-surface` against the canvas, flush
 * left, no border, no radius, no close affordance and nothing to click. §2
 * bans bordered cards and docked panels; a tab that is only a change of
 * background is inside that rule, and a second tab would not be, because then
 * it would be a tab bar and tab bars are what §2 is actually about.
 *
 * <p>The rating sits at the opposite end of the same bar, in the meter's own
 * calm/hot semantics (§6) — green above zero, red below, plain `--color-faint`
 * at exactly zero, since zero is neither an achievement nor a black mark.
 */
export function WindowTab({ status, rating }: Props) {
  const tone = rating > 0 ? 'text-calm' : rating < 0 ? 'text-hot' : 'text-faint'
  const sign = rating > 0 ? '+' : ''

  return (
    <div className="flex w-full shrink-0 items-center gap-3 pr-4 text-[11px] lowercase select-none">
      <span className="flex items-center gap-2 bg-surface px-3 py-1.5">
        <img src="/brand/gpdetox-mark-white.svg" alt="" aria-hidden className="h-3.5 w-3.5" />
        <span className="text-ink">gpdetox</span>
      </span>
      <span className="truncate text-faint">
        {CWD}
        {status && ` — ${status}`}
      </span>
      <span className={`ml-auto shrink-0 tabular-nums ${tone}`} title="your saved rating">
        <span className="sr-only">rating {sign}{rating}</span>
        <span aria-hidden>
          rating {sign}
          {rating}
        </span>
      </span>
    </div>
  )
}
