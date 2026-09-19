# HumanCode — UI Design

The interface has one job: make it feel like **the machine is running the interview and you are the
one being evaluated.** Every layout decision below follows from that inversion.

Reference point is **Monkeytype**, not an IDE. Monkeytype works because the screen is almost empty,
the thing you are doing is dead centre, everything else is dim, and the chrome gets out of the way the
moment you start typing. We want that, with an interviewer's voice hanging over the top of it.

---

## 1. The inversion, stated as layout

In a normal coding site, the problem is a panel and you are a panel — two peers in a split view. That
framing is wrong here. So:

- **The AI speaks from the top of the screen, across the full width.** Not a sidebar, not a chat
  bubble column. It is above you, addressing you. There is no split-pane divider anywhere in the app.
- **You are in the centre**, in a narrow column, with nothing competing for the middle of the screen.
- **The AI's private notes sit on the right**, where you can see it forming opinions about you in
  real time and can do nothing about it.
- **Everything else — meter, clock, stats, problem meta — is on the left**, quiet and out of the way.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│            the standing question, dimmed, one or two lines               │
│                                                                          │
│         ▌ "Still with me? That cursor hasn't moved in a while."          │  ← voice band
│                                                                          │
├────────────────┬─────────────────────────────────────┬───────────────────┤
│                │                                     │                   │
│  impatience    │   function twoSum(nums, target) {   │  private notes    │
│  ▓▓▓▓▓░░░░░    │     const seen = new Map();         │                   │
│  34            │     for (let i = 0; i < ...         │  reached for a    │
│                │   }                                 │  hashmap. some    │
│  04:12         │                                     │  hope.            │
│  written  412  │                                     │                   │
│  deleted  180  │                                     │  idle 22s.        │
│  pastes     1  │                                     │                   │
│                │                                     │  pasted 400 chars │
│  two sum       │                                     │  in one go.       │
│  easy          │                                     │                   │
│                │                                     │                   │
│  [ run ]       │                                     │                   │
│  [ end ]       │                                     │                   │
└────────────────┴─────────────────────────────────────┴───────────────────┘
     216px                    fluid, max 78ch                    264px
```

---

## 2. Anti-goals — things that would make this look like LeetCode

Call these out in review. Any one of them collapses the whole concept back into a practice site.

| Do not | Instead |
|---|---|
| Split pane with a draggable divider | Fixed three-zone layout, no resize handles |
| Bordered cards with rounded corners around every panel | No borders. Zones are separated by whitespace and contrast alone |
| Green/yellow/red difficulty pills | Lowercase plain text: `easy`, `medium`, `hard`, in `--color-sub` |
| Tabs: Description / Solutions / Submissions | There is one view. There are no tabs anywhere |
| A console drawer that slides up from the bottom | Test output goes to the voice band — the interviewer tells you |
| Dense icon toolbars | Two text buttons, lowercase, no icons |
| A big green "Accepted" banner | The interviewer says something begrudging. That is the reward |
| Syntax-highlighted marketing-grade problem statement | Problem is prose in the voice band, examples are mono text on the left |

The general rule: **if it looks like a tool, it is wrong. It should look like a room you are being
interviewed in.**

---

## 3. Tokens

Warm-neutral dark base, one cool accent. The accent is deliberately *not* Monkeytype's amber — we
borrow the restraint, not the palette — and it is kept cool so it never competes with the red/green
semantics of the meter.

Live in `web/src/index.css`.

```css
@theme {
  /* surfaces */
  --color-canvas:  #232427;  /* page */
  --color-surface: #2a2b2f;  /* meter mask, scrollbars — barely distinct */

  /* text */
  --color-ink:     #d6d5cd;  /* primary: code, live utterance */
  --color-sub:     #6b6d72;  /* labels, stats, the standing question */
  --color-faint:   #45474b;  /* dividers, inactive marks */

  /* accent — caret, focus ring, the one highlighted thing */
  --color-accent:  #8b9fff;

  /* meter semantics: calm is good, hot is bad */
  --color-calm:    #4ade80;
  --color-warm:    #fbbf24;
  --color-hot:     #f87171;

  --font-mono: "JetBrains Mono", "Roboto Mono", ui-monospace, monospace;
  --breakpoint-wide: 1100px;
}
```

Tailwind 4 derives the utilities from these names: `bg-canvas`, `text-ink`, `text-sub`, `text-faint`,
`bg-surface`, `text-accent`, `text-hot`, and the `wide:` variant.

**Everything is monospace.** Prose, labels, numbers, buttons, the interviewer's lines. A single
typeface across the whole app is most of the Monkeytype feeling, and it sells "terminal, not product".

Type scale — small and tight:

| Role | Size | Weight | Colour |
|---|---|---|---|
| Live utterance | 20px / 1.5 | 400 | `--color-ink` |
| Standing question | 14px / 1.6 | 400 | `--color-sub` |
| Editor | 14px / 1.7 | 400 | Monaco theme |
| Rail labels | 11px, lowercase, `tracking-wide` | 400 | `--color-sub` |
| Rail values | 11–12px, tabular numerals | 400 | `--color-ink` |
| Notes | 12px / 1.6 | 400 | `--color-sub` |

Lowercase everything in the chrome. `written`, `deleted`, `pastes`, `run`, `end`. Never Title Case,
never ALL CAPS.

---

## 4. Zones

### 4.1 Voice band (top)

The interviewer. Full width, centred content, `max-width: 68ch`, generous vertical padding
(`3rem` top, `2rem` bottom). No background colour, no border — it is separated from the editor by
space alone.

Two stacked elements:

1. **The standing question** — the problem statement, in `--color-sub`, always present. This is the
   thing it asked you, and it does not go away.
2. **The live utterance** — the most recent line, in `--color-ink` at 20px, with a thin
   `--color-accent` vertical rule on its left (3px, full line height). This is the only accent-coloured
   thing on screen besides the caret.

Only **one** utterance is visible at a time. When a new one arrives the old one does not scroll into a
log — it fades out and the new one fades in over it. The transcript is not a chat history; it is a
person talking. History belongs in the report card.

A muted `canned` marker (9px, `--color-faint`, right-aligned) appears when the line came from the
fallback rather than the model. Useful in dev, invisible enough for a demo.

### 4.2 Editor (centre)

Monaco on a custom `humancode` theme, stripped: no minimap, no folding, no glyph margin, no overview
ruler, `renderLineHighlight: 'none'`. Background and gutter must match `--color-canvas` exactly so the
editor reads as the page rather than as an embedded widget.

**Line numbers stay on**, in `--color-faint`. They are the one piece of IDE furniture that earns its
place: the interviewer's notes refer to them ("staring at line 12"), and that joke needs the reader to
be able to look.

Centred, `max-width: 78ch`, so the code column and the voice band above it share the same optical
centre line. This alignment is what makes the whole screen feel composed rather than assembled.

The caret is `--color-accent` and does not blink while typing.

### 4.3 Left rail — instruments

Width `216px` (`13.5rem`). Order top to bottom, because this is descending order of how much it should
worry you:

1. **Impatience meter** (§5)
2. **Clock** — `mm:ss`, tabular numerals, 16px. Counts up. Driven by a local 1s interval, **not** by
   telemetry: telemetry only flushes when there are events, so a server-derived clock stalls the moment
   you stop typing — exactly when the clock matters most.
3. **Stats** — `written`, `deleted`, `pastes`, label left / value right, 11px labels.
4. **Problem meta** — title lowercase, difficulty below it, then examples as compact mono lines
   (`in  [2,7,11,15], 9` / `out [0,1]`). No boxes.
5. **Test result** — after a run only: `tests  4/6`, the count in `--color-calm` when green and
   `--color-hot` when not, with the first failure beneath it in 10px `--color-faint`. This is the one
   place the candidate sees *which* case failed; everything else about the run goes through the
   interviewer.
6. **Actions** — `run`, `end`. Text buttons, `--color-sub`, underline on hover, no fill, no border.

Groups carry no headings except the meter's (see §5); separation is space alone.

**Only items 1–4 scroll.** The test result and the actions are pinned outside the scroll container — a
`run` button that can scroll out of reach is a bug you find mid-demo, which is exactly how this one was
found.

### 4.4 Right rail — private notes

Width `264px`. Header is the joke and must stay: **`private notes`** in `--color-sub`, with
`(do not read)` after it in `--color-faint`.

Notes are a chronological list, newest at the bottom, auto-scrolled. Each note is 12px, `--color-sub`,
with a 2px `--color-faint` left rule and `0.75rem` padding — margin annotations, not chat messages.
Lowercase, no terminal punctuation. They should read like something scribbled, not composed.

New notes fade in over 200ms and nudge up 4px. Nothing more; if the eye is drawn away from the editor
the effect is too strong.

---

## 5. Impatience meter

The one piece of colour semantics in the app, so it must be unambiguous: **green is calm, red is
furious.**

- A lowercase `impatience` label above it, 11px `--color-sub`. The bar alone with a bare number read
  as cryptic in practice — this is the one labelled group in the rail, and the §1 wireframe assumed it.
- Horizontal bar, full rail width, `8px` tall, `border-radius: 9999px`. (Started at 6px; raised for
  legibility from across a room, which is how a judge will see it.)
- The **gradient lives on the track itself**, always at full width.
- A `--color-surface` cover eats the unreached portion from the right.

The technique matters. If you put the gradient on a growing fill element, the fill renders
green-to-red at *every* value and the colour stops meaning anything. Masking from the right instead
keeps each position's colour fixed — at 20 you see green only, at 95 the bar has swept through amber
into red — and needs no width measurement, so it survives any rail size:

```tsx
<div className="relative h-1.5 w-full overflow-hidden rounded-full"
     style={{ background: GRADIENT }}
     role="meter" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}
     aria-label="interviewer impatience">
  <div className="absolute inset-y-0 right-0 bg-surface transition-[width] duration-700 ease-out"
       style={{ width: `${100 - clamped}%` }} />
</div>
```

where `GRADIENT` is
`linear-gradient(90deg, var(--color-calm) 0%, var(--color-warm) 55%, var(--color-hot) 100%)`.

At 20 you see only green. At 60 the bar has reached amber. At 95 it is red and nearly full. The colour
and the length carry the same message, which is what makes it readable at a glance from across a
room — worth caring about, since a judge will be looking at this from two metres away.

Below the bar: the number, 12px tabular, `--color-sub`, right-aligned. No `/100`. Just `34`.

**At ≥85**, the bar pulses: opacity `1 → 0.65 → 1` over 1.6s, infinite, `ease-in-out`. Suppressed
under `prefers-reduced-motion`.

Transitions are `700ms ease-out` on width — slow enough to read as a mood shifting rather than a
progress bar jumping.

---

## 6. Focus mode

The Monkeytype move, and the single most important interaction in the app.

**While typing** (any keystroke, until 1.5s of silence):
- Left rail and right rail → `opacity: 0.25`
- Standing question → `opacity: 0.35`
- Live utterance stays fully visible. The interviewer never dims. It is always watching.

**After 1.5s of silence**: everything returns to full opacity over 400ms.

The result: while you are in flow the screen is almost empty and the interviewer's line floats alone
above your code. The instant you hesitate, the notes and the meter fade back in and you remember you
are being measured. That is the entire emotional design of the product in one transition.

Implementation: a single `typing` boolean on the app root driving a class, debounced off the same
telemetry events already being captured in `EditorPane`. Do not wire this per-component.

---

## 7. Motion

Sparse and slow. Nothing bounces, nothing slides in from off-screen.

| Element | Motion | Duration |
|---|---|---|
| Utterance swap | cross-fade | 250ms |
| Note arrival | fade + 4px rise | 200ms |
| Meter width | width | 700ms ease-out |
| Meter pulse (≥85) | opacity | 1.6s loop |
| Focus mode | opacity | 250ms out / 400ms in |

All of it inside a `prefers-reduced-motion: reduce` guard that drops to instant state changes. The
meter pulse in particular must not run for people who have asked for reduced motion.

---

## 8. Responsive

Below `1100px` the rails do not fit. Collapse, do not compress:

- Voice band stays full width at the top. It is never sacrificed.
- Editor takes the full column.
- Left rail becomes a single horizontal strip under the voice band: meter, clock, and stats inline.
- Right rail becomes a native `<details>` disclosure under the editor, collapsed, showing a note
  count. Native rather than a custom bottom sheet — it is keyboard accessible for free and there is
  nothing to get wrong.

Below `700px` this is a viewing experience, not a working one. Keep it legible; do not optimise it.

---

## 9. Accessibility

- All text meets 4.5:1 against `--color-canvas` **except** `--color-faint`, which is decorative only
  and must never carry information on its own.
- The meter is `role="meter"` with `aria-valuenow/min/max` and a label. The colour is redundant with
  the number, so it is never the sole carrier of meaning.
- Focus mode changes opacity only — never `display` or `visibility` — so screen readers and keyboard
  focus order are unaffected.
- New utterances go in an `aria-live="polite"` region. Notes do **not** — they would interrupt
  constantly and the joke is that they are peripheral.
- Focus rings are `--color-accent`, 2px, always visible on keyboard focus. Never `outline: none`.

---

## 10. Component mapping

Implemented. Recorded here so the intent survives the next refactor:

| Component | Change |
|---|---|
| `App.tsx` | Voice band on top + `[13.5rem, 1fr, 16.5rem]` below. Owns the `typing` flag and the local clock. |
| `ChatPanel.tsx` | ~~Replaced by~~ `VoiceBand.tsx`. Not a scrolling log — renders the standing question plus the single latest utterance. |
| `ProblemPanel.tsx` | ~~Removed.~~ Statement moved into `VoiceBand`, meta and examples into `LeftRail.tsx`. |
| `ImpatienceMeter.tsx` | Rebuilt per §5 — masked gradient, number only, pulse at ≥85. Face/avatar dropped; the mascot is a separate exercise and a placeholder ASCII face undercuts the restraint. |
| `NotesPanel.tsx` | Restyled to margin-annotation treatment, card border removed. |
| `EditorPane.tsx` | Monaco chrome stripped, custom theme matched to `--color-canvas`, `run` moved to the left rail. |
| `index.css` | Token block from §3, focus-mode rules, keyframes, reduced-motion guard, **thin/faint scrollbar styling** — the native scrollbar is bright and reads as exactly the bolted-on chrome §2 forbids. |
| `LeftRail.tsx` | Instruments, scrolling; test result + actions pinned below. |
| `hooks/useTypingFocus.ts` | The single `typing` boolean behind focus mode. |

---

## 11. The one-line test

If a stranger glances at the screen for two seconds, they should think *"something is watching this
person type"* — not *"this is a coding website."* If a change makes the second reading more likely,
it is the wrong change.
