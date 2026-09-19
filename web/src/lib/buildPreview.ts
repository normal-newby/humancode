import type { ProblemFile } from '../api/types'

/** The file whose content is currently in the editor, by name. */
export type FileContents = Record<string, string>

/**
 * Assembles the candidate's files into one self-contained document for the
 * preview iframe (UI-DESIGN.md §4.3b).
 *
 * <p>The iframe has an opaque origin and no server behind it, so a relative
 * `href="styles.css"` resolves to nothing. Every referenced stylesheet and
 * script is therefore inlined into the HTML in the place its tag stood, which
 * also keeps execution order exactly as the candidate wrote it.
 *
 * <p><b>Only referenced files are inlined.</b> If they delete the
 * `<script src="app.js">` tag, their page stops running its script — and the
 * preview shows that, because that is what the page now does. Helpfully
 * appending the orphaned file would make the preview disagree with the page
 * the rubric is judged against, which is worse than a blank screen they can
 * explain.
 */
export function buildPreviewDocument(
  files: ProblemFile[],
  contents: FileContents,
): string | null {
  const html = files.find((file) => isHtml(file))
  if (!html) {
    return null
  }

  let document = contents[html.name] ?? html.starterContent

  for (const file of files) {
    if (file === html) continue
    const content = contents[file.name] ?? file.starterContent

    // The replacement is a FUNCTION, never a string. A string replacement runs
    // the candidate's own code through `$$`, `$&`, `` $` ``, `$'` and `$1`
    // substitution: a `money()` returning `` `$${n.toFixed(2)}` `` has its `$$`
    // collapsed to a single `$`, so the preview renders `12.50` where their
    // code plainly says `$12.50` and they go hunting a bug that is ours.
    if (isCss(file)) {
      document = document.replace(linkTag(file.name), () => `<style>\n${sealCss(content)}\n</style>`)
    } else if (isJs(file)) {
      document = document.replace(
        scriptTag(file.name),
        () => `<script>\n${sealJs(content)}\n</script>`,
      )
    }
  }

  return document
}

/** True when this problem has something to render at all. */
export function hasPreview(files: ProblemFile[]): boolean {
  return files.some(isHtml)
}

function isHtml(file: ProblemFile): boolean {
  return file.language === 'html' || file.name.endsWith('.html')
}

function isCss(file: ProblemFile): boolean {
  return file.language === 'css' || file.name.endsWith('.css')
}

function isJs(file: ProblemFile): boolean {
  return file.language === 'javascript' || file.name.endsWith('.js')
}

/** `styles.css`, `./styles.css` and `/styles.css` are all the same file here. */
function pathPattern(name: string): string {
  return `[.]?/?${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`
}

function linkTag(name: string): RegExp {
  return new RegExp(`<link\\b[^>]*href\\s*=\\s*["']${pathPattern(name)}["'][^>]*>`, 'gi')
}

function scriptTag(name: string): RegExp {
  return new RegExp(
    `<script\\b[^>]*src\\s*=\\s*["']${pathPattern(name)}["'][^>]*>\\s*</script\\s*>`,
    'gi',
  )
}

/**
 * The HTML parser ends a `<script>` at the first literal `</script`, wherever
 * it appears — including inside a string. A candidate writing
 * `el.innerHTML = '</script>'` would otherwise blow the document apart and see
 * a blank preview with nothing in their code to explain it.
 */
function sealJs(code: string): string {
  return code.replace(/<\/(script)/gi, '<\\/$1')
}

function sealCss(code: string): string {
  return code.replace(/<\/(style)/gi, '<\\/$1')
}
