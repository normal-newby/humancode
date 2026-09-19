/**
 * The interviewer, rendered as a face (CLAUDE.md "Fun" tier: an avatar whose
 * expression escalates with impatience). It is a mascot, not a vendor logo —
 * and it is glyphs, not an image, because UI-DESIGN.md §2 forbids avatars,
 * chat bubbles and emoji outright. A monospace face bracketed like a `[head]`
 * is the only thing that reads as "someone is watching" without turning the
 * terminal into a chat app.
 *
 * <p>It sits with the meter (§6) and shares its semantics: calm green as it
 * watches, warm as it tires of you, hot when it has had enough — and at ≥85 it
 * pulses on the same clock as the number, because that is where the meter says
 * the human has stopped being reasonable.
 *
 * <p>Every stage is exactly three inner glyphs, so the head never changes
 * width and the footer never reflows as the mood turns. Eight stages, not
 * five — five left long stretches of the range visibly static and put two
 * near-identical faces (`·_·`, `-_-`) back to back, which read as one face
 * that never moved. Each stage here is a distinct silhouette, and the head
 * pops (`animate-face-pop`, keyed on the face itself) the instant it changes
 * stage, so a jump reads as a reaction, not a redraw.
 */
const STAGES = [
  { max: 9, face: '•‿•', mood: 'still charmed', cls: 'text-calm' },
  { max: 24, face: '·_·', mood: 'watching you', cls: 'text-calm' },
  { max: 39, face: '-_-', mood: 'unimpressed', cls: 'text-calm' },
  { max: 54, face: '¬_¬', mood: 'side-eyeing you', cls: 'text-warm' },
  { max: 69, face: 'ò_ó', mood: 'losing patience', cls: 'text-warm' },
  { max: 84, face: '>_<', mood: 'gritting their teeth', cls: 'text-hot' },
  { max: 94, face: '×_×', mood: 'furious', cls: 'text-hot' },
  { max: 100, face: '╬_╬', mood: 'seeing red', cls: 'text-hot' },
] as const

export function InterviewerFace({ impatience }: { impatience: number }) {
  const clamped = Math.min(100, Math.max(0, Math.round(impatience)))
  const stage = STAGES.find((s) => clamped <= s.max) ?? STAGES[STAGES.length - 1]
  const furious = clamped >= 85

  return (
    <span
      className={`tabular-nums ${stage.cls} ${furious ? 'animate-meter-pulse' : ''}`}
    >
      {/* The mood, not the number — the meter beside it already announces the
          percentage, and repeating it here would double up in the reader. */}
      <span className="sr-only">the interviewer is {stage.mood}</span>
      <span aria-hidden className="text-faint">
        [
        <span key={stage.face} className={`inline-block animate-face-pop ${stage.cls}`}>
          {stage.face}
        </span>
        ]
      </span>
    </span>
  )
}
