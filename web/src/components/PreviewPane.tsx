interface Props {
  /** A complete HTML document, already assembled by buildPreviewDocument. */
  document: string | null
}

/**
 * The candidate's own page, rendered (UI-DESIGN.md §4.3b).
 *
 * <p><b>`sandbox="allow-scripts"` and nothing else.</b> Omitting
 * `allow-same-origin` is the whole protection: the frame gets an opaque
 * origin, so the code inside cannot reach the parent document, the session,
 * or storage — it can only paint itself. The two flags together would undo
 * that, which is the one mistake worth calling out here, since it looks like
 * a way to fix `localStorage` throwing.
 *
 * <p>Running their code in their own browser needs no sandbox
 * infrastructure and carries no server-side RCE surface — the same reasoning
 * the old in-browser test runner was built on (CLAUDE.md §6).
 */
export function PreviewPane({ document }: Props) {
  if (!document) {
    return (
      <p className="pt-2 text-[13px] text-faint">
        nothing to render — this problem has no html file
      </p>
    )
  }

  return (
    <iframe
      // Remounting on every change would scroll the page back to the top and
      // flash white; srcDoc on a stable frame swaps the document in place.
      title="your page"
      srcDoc={document}
      sandbox="allow-scripts"
      className="h-full w-full border-0 bg-white"
    />
  )
}
