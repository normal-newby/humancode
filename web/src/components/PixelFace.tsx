import type { Mood } from '../api/types'

/**
 * The human on the other side, drawn as pixels (UI-DESIGN.md §6a).
 *
 * <p>It is the one avatar this UI allows, and it earns the exception by being
 * the product: "finding out how you did by reading the interviewer's face" is
 * §4.7's whole argument for having no pass count anywhere. Every prompt in the
 * log carries the face they wore while typing it, so the verdict on your code
 * arrives as an expression before you have read a word of the sentence.
 *
 * <p>Drawn, not imported: a sprite of `<rect>`s on a 12x12 grid, one colour
 * from the palette per mood and no image file, no emoji and no vendor mark.
 * The head is the same silhouette in all five; only the brows, eyes and mouth
 * move, which is what makes two faces a beat apart read as the same person
 * changing their mind rather than two different drawings.
 */

/** `h` head, `f` feature (brow, eye, mouth), `.` nothing. */
const FACES: Record<Mood, string[]> = {
  IMPRESSED: [
    '...hhhhhh...',
    '.hhhhhhhhhh.',
    'hhffhhhhffhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhfhhhhfhhh',
    'hhhhhhhhhhhh',
    'hhhfhhhhfhhh',
    'hhhhffffhhhh',
    '.hhhhhhhhhh.',
    '..hhhhhhhh..',
    '....hhhh....',
  ],
  AMUSED: [
    '...hhhhhh...',
    '.hhhhhhhhhh.',
    'hhhhhhhhffhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhfhhhhfhhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhfhhh',
    'hhhhfffhhhhh',
    '.hhhhhhhhhh.',
    '..hhhhhhhh..',
    '....hhhh....',
  ],
  NEUTRAL: [
    '...hhhhhh...',
    '.hhhhhhhhhh.',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhfhhhhfhhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhhffffhhhh',
    '.hhhhhhhhhh.',
    '..hhhhhhhh..',
    '....hhhh....',
  ],
  IMPATIENT: [
    '...hhhhhh...',
    '.hhhhhhhhhh.',
    'hhhhhhhhhhhh',
    'hhffhhhhffhh',
    'hhhhhhhhhhhh',
    'hhhfhhhhfhhh',
    'hhhhhhhhhhhh',
    'hhhhhhhhhhhh',
    'hhhffffffhhh',
    '.hhhhhhhhhh.',
    '..hhhhhhhh..',
    '....hhhh....',
  ],
  EXASPERATED: [
    '...hhhhhh...',
    '.hhhhhhhhhh.',
    'hhhhhhhhhhhh',
    'hhfhhhhhhfhh',
    'hhhffhhffhhh',
    'hhhfhhhhfhhh',
    'hhhhhhhhhhhh',
    'hhhffffffhhh',
    'hhfhhhhhhfhh',
    '.hhhhhhhhhh.',
    '..hhhhhhhh..',
    '....hhhh....',
  ],
}

/** One palette colour per mood — the meter's own three, plus `sub` for nothing-in-particular. */
const TONE: Record<Mood, string> = {
  IMPRESSED: 'text-calm',
  AMUSED: 'text-calm',
  NEUTRAL: 'text-sub',
  IMPATIENT: 'text-warm',
  EXASPERATED: 'text-hot',
}

/** Second person about them, the way the rest of the log talks. */
const DESCRIPTION: Record<Mood, string> = {
  IMPRESSED: 'the human looks impressed',
  AMUSED: 'the human looks amused',
  NEUTRAL: 'the human looks unimpressed',
  IMPATIENT: 'the human looks annoyed',
  EXASPERATED: 'the human looks furious',
}

/** Horizontal runs, not one rect per pixel: a face is ~16 nodes instead of 144. */
function runs(rows: string[], tone: 'h' | 'f') {
  const out: { x: number; y: number; w: number }[] = []
  rows.forEach((row, y) => {
    let x = 0
    while (x < row.length) {
      if (row[x] !== tone) {
        x += 1
        continue
      }
      const start = x
      while (x < row.length && row[x] === tone) x += 1
      out.push({ x: start, y, w: x - start })
    }
  })
  return out
}

interface Props {
  mood: Mood
  /** Tailwind sizing for the sprite, e.g. `h-5 w-5`. */
  className?: string
  /**
   * Overrides the mood colour — what the log uses to let an older prompt's
   * face recede to `--color-faint` with the rest of its block.
   */
  tone?: string
  /** False on an older prompt, where a talking head would be noise. */
  animate?: boolean
}

export function PixelFace({ mood, className = 'h-5 w-5', tone, animate = false }: Props) {
  const rows = FACES[mood] ?? FACES.NEUTRAL

  return (
    <span className={`inline-block align-middle ${tone ?? TONE[mood]} ${className}`}>
      <span className="sr-only">{DESCRIPTION[mood] ?? DESCRIPTION.NEUTRAL}</span>
      <svg
        viewBox="0 0 12 12"
        className={`h-full w-full ${animate ? 'animate-face-pop' : ''}`}
        shapeRendering="crispEdges"
        aria-hidden
        focusable="false"
      >
        {/* The head is the same colour, held back — one tone per face keeps it
            terminal rather than cartoon, and the features still read at 20px. */}
        {runs(rows, 'h').map(({ x, y, w }) => (
          <rect key={`h-${x}-${y}`} x={x} y={y} width={w} height={1} fill="currentColor" opacity={0.3} />
        ))}
        {runs(rows, 'f').map(({ x, y, w }) => (
          <rect key={`f-${x}-${y}`} x={x} y={y} width={w} height={1} fill="currentColor" />
        ))}
      </svg>
    </span>
  )
}

/**
 * The mood to wear when nobody said one — the report card, where the session is
 * over and all that is left of how it went is where the meter stopped.
 *
 * <p>This is deliberately read off the impatience number and nothing else.
 * `GeneratedReport.outcome` knows whether the app actually works and never
 * reaches the browser (CLAUDE.md §5), and a face driven by it would be the
 * pass/fail badge §4.7 exists to forbid, drawn instead of written. The meter is
 * already on screen, so a face that agrees with it leaks nothing new.
 */
export function moodForImpatience(impatience: number): Mood {
  const level = Math.min(100, Math.max(0, Math.round(impatience)))
  if (level < 15) return 'IMPRESSED'
  if (level < 35) return 'AMUSED'
  if (level < 60) return 'NEUTRAL'
  if (level < 85) return 'IMPATIENT'
  return 'EXASPERATED'
}
