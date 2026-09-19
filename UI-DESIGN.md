# HumanCode — UI Design

The interface has one job: make it feel like **the machine is running the interview and you are the
one being evaluated.** Every layout decision below follows from that inversion.

Reference point is **Claude Code**, not an IDE and not a practice site. A coding agent's terminal
works because it is one column of transcript: the machine speaks, stamps what it just did in a dim
line underneath, and waits at a prompt box at the bottom. You are always reading upward at something
that has already formed an opinion. That grammar is free comedy for this product — we are reusing the
*shape* of an agent's log, with the roles swapped.

We borrow the grammar, never the branding. No vendor logo, no vendor wordmark, no vendor model names
in the chrome. The accent below is ours.

---

## 1. The inversion, stated as layout

In a normal coding site, the problem is a panel and you are a panel — two peers in a split view. That
framing is wrong here. So:

- **One centred column.** No rails, no split panes, no dividers, nothing docked to an edge.
- **The interviewer's turns are the transcript**, marked with `⏺`, scrolling upward as the session
  goes on. You read them the way you read an agent's output: top-down, after the fact.
- **Every turn carries a dim meta line underneath it** — elapsed time first, then what you typed in
  that beat, formatted exactly like a token-usage readout. This is the spine of the design; §5 is the
  whole spec.
- **Your code is the composer**, a bordered prompt box pinned to the bottom of the column, where the
  input box sits in a terminal agent.
- **One status line under the composer** carries everything else: what you are doing right now, the
  clock, session totals, impatience, key hints.

```
      ⏺ two sum. given an array of integers and a target, return
        the indices of the two numbers that add to it. in any order.
        ⎿  04:12 · ↑ 1.2k · ↓ 431 · ⧉ 2                     ← live, pinned
      ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
      ⏺ nested loop. bold choice for an array this size.
        ⎿  01:38 · ↑ 214 · ↓ 12

      ⏺ still with me? that cursor hasn't moved in a wh▍   ← revealing, §4.6

      ✻ taking notes… (3)
                                                            ← transcript scrolls
   ╭──────────────────────────────────────────────────────╮
   │ > function twoSum(nums, target) {                    │
   │     const seen = new Map();                          │
   │     for (let i = 0; i < nums.length; i++) {          │
   │   }                                                  │
   ╰──────────────────────────────────────────────────────╯
     ✻ contemplating…   04:12   ↑1.2k ↓431 ⧉2   impatience 34% ▓▓▓░░░   ⏎ run   esc end
```

Column is `max-width: 84ch`, centred, with the composer and status line pinned to the bottom of the
viewport and the transcript scrolling behind them. The transcript's left text edge and the composer's
left text edge sit on the same optical line — that single alignment is most of why the screen reads as
one surface rather than three stacked widgets.

**The transcript keeps its scrollback.** This reverses an earlier rule ("one utterance at a time, no
history"). A meta line is a log stamp; it only means anything if the thing it stamps stays on screen.
Earlier turns drop to `--color-faint` so the newest line is still unmistakably the live one.

---

## 2. Anti-goals — things that would make this look like LeetCode

Call these out in review. Any one of them collapses the whole concept back into a practice site.

| Do not | Instead |
|---|---|
| Split pane with a draggable divider | One column, no resize handles, nothing docked to an edge |
| Bordered cards with rounded corners around every panel | Exactly **one** border in the app: the composer box. Everything else is separated by whitespace and contrast |
| Green/yellow/red difficulty pills | Lowercase plain text: `easy`, `medium`, `hard`, in `--color-sub` |
| Tabs: Description / Solutions / Submissions | There is one view. There are no tabs anywhere |
| A console drawer that slides up from the bottom | There is no test output on screen at all. The interviewer tells you — §4.7 |
| A pass count, a failure list, a green check anywhere in the session | The run result is a signal for the model, never a readout for the candidate |
| Dense icon toolbars | Key hints in the status line — `⏎ run`, `esc end`. No filled buttons, no icons |
| A big green "Accepted" banner | The interviewer says something begrudging. That is the reward |
| Chat bubbles, avatars, speech tails, alternating alignment | Flush-left transcript lines with a `⏺` marker. Nobody's terminal has bubbles |
| Emoji anywhere in the chrome | The glyph set in §4.1 and nothing else |

The general rule: **if it looks like a tool, it is wrong. It should look like a session log of someone
being judged.**

---

## 3. Tokens

Warm near-black, the way a terminal on a dark theme reads, with one warm accent. The accent is
deliberately warm-coral so it never collides with the red/green semantics the impatience meter owns,
and it is ours — do not reach for a vendor's brand colour to sell the resemblance.

Live in `web/src/index.css`.

```css
@theme {
  /* surfaces */
  --color-canvas:  #1c1b19;  /* page — warm near-black, terminal-dark */
  --color-surface: #24231f;  /* composer fill, meter track mask */

  /* text */
  --color-ink:     #e6e2d8;  /* live turn, code */
  --color-sub:     #8a857a;  /* meta lines, status line, notes */
  --color-faint:   #4b4841;  /* past turns, glyphs, the one border */

  /* accent — caret, composer focus border, the ⏺ on the live turn */
  --color-accent:  #d98b63;

  /* meter semantics: calm is good, hot is bad */
  --color-calm:    #6bbf7b;
  --color-warm:    #d8a13c;
  --color-hot:     #d96a5e;

  --font-mono: "JetBrains Mono", "Roboto Mono", ui-monospace, monospace;
  --breakpoint-wide: 900px;
}
```

Tailwind 4 derives the utilities from these names: `bg-canvas`, `text-ink`, `text-sub`, `text-faint`,
`bg-surface`, `text-accent`, `text-hot`, and the `wide:` variant.

**Everything is monospace.** Prose, numbers, the status line, the interviewer's lines. One typeface
across the whole app is most of the terminal feeling, and it makes the meta lines align for free.

Type scale — small and tight. A terminal has essentially one size; the thing that varies is colour:

| Role | Size | Weight | Colour |
|---|---|---|---|
| Live turn | 15px / 1.6 | 400 | `--color-ink` |
| Past turn | 15px / 1.6 | 400 | `--color-faint` |
| Meta line | 12px / 1.5, tabular numerals | 400 | `--color-sub`, glyphs `--color-faint` |
| Editor | 14px / 1.7 | 400 | Monaco theme |
| Status line | 13px, tabular numerals | 400 | `--color-sub` |
| Notes | 12px / 1.6 | 400 | `--color-sub` |

Lowercase everything, including the interviewer's own lines. `two sum`, `easy`, `run`, `end`,
`idle 47s`. Never Title Case, never ALL CAPS. A terminal does not shout.

---

## 4. Zones

### 4.1 Glyphs

The whole vocabulary. Adding to this list is a design change, not an implementation detail.

| Glyph | Code point | Means |
|---|---|---|
| `⏺` | U+23FA | an interviewer turn begins |
| `⎿` | U+23BF | the meta line, or a detail attached to the turn above |
| `✻` | U+273B | the interviewer is thinking / taking notes |
| `>` | U+003E | the composer prompt |
| `↑` `↓` | U+2191 / U+2193 | characters written / deleted |
| `⧉` | U+29C9 | a paste |
| `▍` | U+258D | the interviewer is mid-sentence (§4.6) |

All glyphs are `--color-faint` and `aria-hidden` — they are texture, never the only carrier of
meaning (§10). Two exceptions, both `--color-accent`: the `⏺` on the newest turn, and the `▍` caret
while it is still speaking. With the editor's caret and the composer's focus border, those are the
only accented things on screen.

### 4.2 Transcript

Flush-left, `max-width: 84ch`, `1.25rem` between turns. A turn is the `⏺` marker, the line itself
hanging-indented to clear the marker, then its meta line indented to match.

The **problem statement is the first turn and it is sticky** to the top of the scroll container, at
`--color-ink` even after other turns have dimmed. It is the thing it asked you and it does not go
away. A `┄` hairline in `--color-faint` sits under it so it reads as pinned rather than as the newest
line.

Everything below it scrolls, auto-stuck to the bottom unless the candidate has scrolled up. Turns
older than the newest are `--color-faint`. Nothing is ever removed — the log is the point — but cap
the DOM at the last 50 turns; the report card owns the full history.

A muted `canned` marker (9px, `--color-faint`) sits at the right end of the meta line when the line
came from the fallback rather than the model. Useful in dev, invisible enough for a demo.

Nothing else is rendered in this column. In particular, no test verdict ever lands here — see §4.7.

### 4.3 Notes — the thinking block

The `private notes (do not read)` sidebar is gone; the joke moves into the terminal idiom, where it is
better. At the foot of the log, under the newest turn, a `✻` line sits in `--color-sub`:

```
✻ taking notes… (3)
```

Click it — or press `ctrl+o` — and it expands in place, each note on its own `⎿` line, 12px
`--color-sub`, lowercase, no terminal punctuation. They should read like something scribbled, not
composed:

```
✻ taking notes… (3)
  ⎿  reached for a hashmap. some hope
  ⎿  staring at line 12 for 94 seconds
  ⎿  pasted 400 chars in one go
```

Collapsed by default, and it stays collapsed — the candidate choosing to peek and finding out what it
thinks of them is the beat. The count animates when it increments; the block never opens itself.

While something is in flight the same line reads `✻ running tests…`, using the shared `Spinner`
(§4.1). Latency the user can see is latency the user forgives.

It currently covers the local test run only. Showing it for a model call needs a signal the server
does not send yet — the SSE channel carries the finished utterance, nothing before it. A
`thinking`/`spoke` pair of events on the stream would be enough, and the component already takes the
label as a prop.

### 4.4 Composer — the editor

Monaco, dressed as a terminal input box. Pinned to the bottom of the column, `min-height: 40vh`,
growing to `60vh` as the code does.

- **A 1px `--color-faint` border, `6px` radius** — the one border in the app (§2). On focus it goes
  `--color-accent`. This is what makes the code read as *your prompt*, the thing the machine is
  waiting on.
- Fill is `--color-surface`, one step off the canvas. Monaco's background and gutter must match it
  exactly so the editor reads as the box rather than as an embedded widget.
- A `>` in `--color-faint` beside line 1, in the box's left padding. Beside, not instead of: swapping
  it in for the line number would read better and cost the notes their "staring at line 12" joke.
- Stripped: no minimap, no folding, no glyph margin, no overview ruler,
  `renderLineHighlight: 'none'`.
- **Line numbers stay on**, in `--color-faint`. They are the one piece of IDE furniture that earns its
  place: the notes refer to them ("staring at line 12"), and that joke needs the reader to be able to
  look.
- Caret is `--color-accent` and does not blink while typing.

### 4.5 Status line

One line under the composer, 13px `--color-sub`, never wraps above `900px`, with the key hints
right-aligned. It is the heaviest of the small type in the app on purpose: it is the only thing
below the composer, and at 11px it read as a footer rather than as instrumentation.

```
✻ contemplating…   04:12   ↑1.2k ↓431 ⧉2   impatience 34% ▓▓▓░░░   ⏎ run   esc end
```

| Segment | Content |
|---|---|
| activity | the cycling `✻` and one lowercase word for what *you* are doing, `…` after it |
| clock | `mm:ss`, counting up, tabular |
| totals | session totals — same glyphs as the meta lines, so the eye connects them |
| impatience | §6 |
| hints | `⏎ run`, `esc end`. Keys in `--color-faint`, words in `--color-sub` |

**The problem's title and difficulty are not here, and are nowhere.** The statement said what the
problem is; repeating `two sum · easy` under the composer for the whole session is a label on a
product, and it was the one dead pixel in the bar. What replaces it moves.

**The activity word.** A cycling `✻` and one lowercase gerund for what the candidate is doing, `…`
after it. It is the inversion in a single word: a coding agent's spinner narrates what *it* is doing
while you wait, and this one narrates what *you* are doing, because in this room you are the one being
waited on.

| State | Pool |
|---|---|
| typing | `writing`, `typing`, `scribbling`, `composing`, `hacking`, `cranking` |
| idle | `thinking`, `contemplating`, `pondering`, `deliberating`, `ruminating`, `mulling` |
| idle, impatience ≥ 60 | `stalling`, `hesitating`, `stewing`, `wavering`, `reconsidering` |
| running | `running`, `checking`, `judging` |

It re-picks the instant the state changes and then every 3.5s while the state holds, never twice in a
row on the same word. The second idle pool is the meter leaking into the language: past 60 it stops
being generous about the pause, and it says so without the interviewer having to spend a turn on it.

Adding words is free and welcome; adding a *state* is not — each one needs a signal that is actually
distinguishable from the other three, or the word stops meaning anything.

The clock is driven by a local 1s interval, **not** by telemetry: telemetry only flushes when there
are events, so a server-derived clock stalls the moment you stop typing — exactly when the clock
matters most.

`⏎` is `ctrl+enter` in the editor (plain enter is a newline, obviously) and both hints are also real
click targets, because a judge at a demo table will reach for the mouse.

**`esc` ends the session on the second press, not the first.** The hint flips to `esc again to end`
in `--color-hot` for three seconds, then disarms. A candidate lives inside the editor and will hit
Escape by reflex; one stray keystroke must not throw away an interview. It is also the idiom the
reference uses for exactly this reason.

Worked examples (`in [2,7,11,15], 9` / `out [0,1]`) are **not** here, and they are not under the
problem statement either — they are not anywhere. See §4.7.

### 4.6 The interviewer types

**Every line the interviewer speaks is revealed a character at a time**, including the problem
statement on arrival. Nothing it says ever appears all at once.

This is not ornament. A line that pops into existence is a notification; a line that types is someone
in the room composing a thought at you, and you sit there reading at their pace, unable to skip ahead.
It also covers the model's latency with something that looks intentional.

- A `▍` block caret in `--color-accent` trails the text and disappears on the last character.
- Long lines reveal **several characters per tick rather than typing slower** — the reveal is paced to
  finish in ~2.8s whether it is a three-sentence problem statement or a six-word insult. A statement
  that takes twelve seconds to deliver is not a joke, it is a loading screen.
- **The meta line waits for the line to finish.** Under the pinned statement, so do the examples and
  the clock. §8's 300ms delay applies from that point, not from arrival: the receipt lands after the
  sentence, never during it.
- The transcript keeps following the growing line, but only if the candidate was already at the
  bottom. Scrolling up to re-read an earlier turn must not get yanked back.
- Under `prefers-reduced-motion` every line is simply there, meta line and all.

Only turns that arrive while you are watching type. A turn that was already on screen re-renders
whole — nothing re-types on a React re-render, which would be a nasty flicker every keystroke.

### 4.7 Test results are never shown

The candidate presses `run`. They get a spinner, and then the interviewer says something. **They never
see a pass count, a failure list, a green check or a red cross.**

Nor do they see **worked examples**. An `in [2,7,11,15], 9` / `out [0,1]` pair is a test case with
better manners: it hands over a case the candidate can eyeball their way to, and it turns the opening
turn into a spec sheet instead of a question someone just asked you. The statement carries the problem
in prose, the way it would be said out loud, and that is all.

`Problem.examples` still ships in the session payload and nothing reads it now — the UI dropped it and
`PromptAssembler`'s prefix never carried it (it sends the statement, reference solution, complexity
and rubric). Either feed it to the model or strip it from `forCandidate()`; leaving it in the payload,
unused, is how it ends up back on the page.

The result goes to the server as a gauge of progress — it is what `TESTS_PASSED` / `TESTS_FAILED`
fire on, and what the interviewer reasons about — and that is the only place it exists. Finding out
whether you passed by reading the interviewer's face is the entire product. The moment a `4/6` appears
on screen, the candidate reads the number, ignores the sentence, and you have built LeetCode with a
mascot.

What the candidate *does* get is the spinner while the run is in flight (`✻ running tests…`) and the
`run` hint reading `running…`. That is feedback that the button worked, not a verdict.

This is also why the run affordance stays cheap to press: pressing it is asking the interviewer how
you are doing, and being told in words.

---

## 5. The meta line

The centrepiece, and the thing most likely to get watered down by someone who reads it as decoration.
It is the product's thesis in one row: **while you were quiet, it was counting.**

Format, indented to align under the turn's first character:

```
⎿  04:12 · ↑ 412 · ↓ 180 · ⧉ 1 · idle 22s
```

Rules:

1. **Time first, always.** Elapsed session time at the moment that line was spoken — `mm:ss`, tabular.
   It is the one segment that is never omitted, because "how long have I been at this" is the pressure
   the whole product runs on.
2. **`↑` written, `↓` deleted** — characters, not tokens, but formatted like a token readout:
   thousands as `1.2k`, one decimal, no unit word. The resemblance is the joke and it is also honest;
   these are the units that actually matter to a person typing.
3. **`⧉` pastes**, count only.
4. **`idle Ns`** appears only when the trigger that fired was an idle trigger. It is the receipt for
   the line above it.
5. **Segments with a zero value are omitted** — except on an idle turn, where `↑ 0 · ↓ 0` is the point.
6. Separator is ` · ` in `--color-faint`. Numbers are `--color-sub`, tabular, never bold.

**The numbers are per-turn deltas — what you did since the previous turn — not running totals.** This
is the direct analogue of a per-message token count: the line tells you what that beat cost you.
Session totals live in the status line (§4.5), the way a context readout does. Getting this backwards
makes every meta line a near-duplicate of the one above it and the row stops carrying information.

**One exception: the pinned problem statement's meta line is live.** It ticks every second and its
counters are session totals, because that turn *is* the session. Every other meta line freezes the
instant its turn is spoken and never changes again. That contrast — one clock running, all the others
stopped — is what makes the transcript read as a log instead of a dashboard.

Sourcing: all four values already exist client-side in the telemetry being batched (`written`,
`deleted`, `pastes`) and in the trigger digest the server attaches to an utterance. The client
computes the delta at render time from counters it already holds; **do not add a round-trip for
this.**

---

## 6. Impatience meter

The one piece of colour semantics in the app, so it must be unambiguous: **green is calm, red is
furious.** It lives inline in the status line now, not in a rail — `impatience 34%` followed by a
short bar, because a terminal status line is exactly where a percentage-with-a-bar belongs.

- Label `impatience`, 11px `--color-sub`, then the number, then the bar. No `/100`.
- Bar is `96px` wide, `8px` tall, `border-radius: 9999px`, vertically centred on the text.
- The **gradient lives on the track itself**, always at full width.
- A `--color-surface` cover eats the unreached portion from the right.

The technique matters. If you put the gradient on a growing fill element, the fill renders
green-to-red at *every* value and the colour stops meaning anything. Masking from the right instead
keeps each position's colour fixed — at 20 you see green only, at 95 the bar has swept through amber
into red — and it needs no width measurement:

```tsx
<div className="relative h-1.5 w-18 overflow-hidden rounded-full"
     style={{ background: GRADIENT }}
     role="meter" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}
     aria-label="interviewer impatience">
  <div className="absolute inset-y-0 right-0 bg-surface transition-[width] duration-700 ease-out"
       style={{ width: `${100 - clamped}%` }} />
</div>
```

where `GRADIENT` is
`linear-gradient(90deg, var(--color-calm) 0%, var(--color-warm) 55%, var(--color-hot) 100%)`.

**At ≥85**, the number — not the bar — goes `--color-hot` and pulses opacity `1 → 0.65 → 1` over 1.6s,
infinite, `ease-in-out`. At this size a pulsing bar is invisible and a pulsing number is not.
Suppressed under `prefers-reduced-motion`.

Transitions are `700ms ease-out` on width — slow enough to read as a mood shifting rather than a
progress bar jumping.

---

## 7. Focus mode

The single most important interaction in the app, and it survives the restyle unchanged in spirit.

**While typing** (any keystroke, until 1.5s of silence):
- Past turns and the `✻` notes line → `opacity: 0.25`
- The pinned problem statement and its live meta line → `opacity: 0.35`
- Status line → `opacity: 0.25`, **except the activity word**, which never dims
- The newest turn stays fully visible. The interviewer never dims. It is always watching.

**After 1.5s of silence**: everything returns to full opacity over 400ms.

The result: while you are in flow the screen is your composer and one line above it. The instant you
hesitate, the log and the counters fade back in and you remember that all of it was being written
down. That is the entire emotional design of the product in one transition.

The activity word is exempt for the same reason the newest turn is: it is the interviewer's read on
you, not instrumentation. Dimming it while typing would hide it in the one state it exists to
report — you would see `thinking…` forever and never once see `writing…`.

Implementation: a single `typing` boolean on the app root driving a class, debounced off the same
telemetry events already captured in the editor. Do not wire this per-component. The same boolean
picks the activity pool, so the two can never disagree.

---

## 8. Motion

Sparse and slow. Nothing bounces, nothing slides in from off-screen.

| Element | Motion | Duration |
|---|---|---|
| Line reveal (§4.6) | character by character, 16ms tick | ~2.8s, any length |
| Activity word (§4.5) | swap, no transition | re-picks every 3.5s |
| `✻` spinner | glyph cycle `✻ ✳ ✢ ✳` | 600ms/frame |
| Caret `▍` while revealing | none — solid, does not blink | — |
| New turn | fade + 4px rise | 200ms |
| Previous turn dimming to faint | colour | 400ms |
| Meta line | fade, 300ms after its line finishes revealing | 200ms |
| Meter width | width | 700ms ease-out |
| Impatience number pulse (≥85) | opacity | 1.6s loop |
| Focus mode | opacity | 250ms out / 400ms in |
| Notes expand | height + fade | 200ms |

The meta line arriving a beat *after* its turn is deliberate: you read the sentence, then the receipt
lands under it. Simultaneous, it is noise; delayed, it is a verdict.

All of it inside a `prefers-reduced-motion: reduce` guard that drops to instant state changes. The
pulse, the spinner and the line reveal in particular must not run for people who have asked for
reduced motion — the spinner falls back to a static `✻`, and lines simply appear. The activity word
still rotates: it is information, not animation.

---

## 9. Responsive

The single column is most of the responsive work already done. Below `900px`:

- Column goes to `100% - 2rem`. Nothing else changes structurally.
- The status line wraps to two rows — problem and clock on the first, totals and impatience on the
  second. The key hints drop; there is a touch keyboard in the way of both of them anyway.
- Composer keeps `min-height: 40vh` and stops growing.

Below `700px` this is a viewing experience, not a working one. Keep it legible; do not optimise it.

---

## 10. Accessibility

- All text meets 4.5:1 against `--color-canvas` **except** `--color-faint`, which carries past turns,
  glyphs and the composer border. Past turns are still fully legible in the report card, and no glyph
  carries meaning on its own.
- **Every glyph is `aria-hidden`** with an `sr-only` word beside it. `↑ 412` announces as
  "412 characters written", not "up arrow 412". This is the easiest thing here to get wrong.
- The meta line is a `<dl>`-shaped structure semantically or, failing that, one `aria-label` on the
  whole row. Do not ship it as a bare string of glyphs and numbers.
- The meter is `role="meter"` with `aria-valuenow/min/max` and a label. The colour is redundant with
  the number, so it is never the sole carrier of meaning.
- New turns go in an `aria-live="polite"` region. Meta lines and notes do **not** — they would
  interrupt constantly, and the joke is that they are peripheral.
- **A revealing line carries its full text in an `sr-only` span from the first frame**, and the
  animating copy is `aria-hidden`. Announcing a growing prefix sixty times a second would make the
  live region unusable. Nobody waits out a typing animation to hear the question.
- The `✻` notes block is a real `<button>` with `aria-expanded`, not a clickable div.
- Focus mode changes opacity only — never `display` or `visibility` — so focus order and screen
  readers are unaffected.
- Focus rings are `--color-accent`, 2px, always visible on keyboard focus. Never `outline: none`.

---

## 11. Component mapping

Implemented. Recorded here so the intent survives the next refactor:

| Component | Change |
|---|---|
| `App.tsx` | One centred column, `max-width: 84ch`; transcript scrolls, composer + status line pinned. Drops the `[13.5rem, 1fr, 16.5rem]` grid. Still owns the `typing` flag and the local clock. |
| `VoiceBand.tsx` | → `Transcript.tsx`. Keeps scrollback instead of swapping one utterance at a time; renders `⏺` turns and the sticky problem statement, each typing itself out. |
| *new* `MetaLine.tsx` | §5. Per-turn deltas, frozen on mount; the live variant under the problem statement takes the session counters and a 1s tick. |
| `NotesPanel.tsx` | → `NotesBlock.tsx`. Collapsed `✻ taking notes… (n)` at the foot of the transcript, not a rail. Doubles as the in-flight spinner. |
| `LeftRail.tsx` | Removed. Clock, stats and impatience → `StatusLine.tsx`; problem meta → status line; examples → `⎿` lines under the problem statement; `run`/`end` → key hints. |
| `ImpatienceMeter.tsx` | Same masking technique, resized to `72×6` for the status line; pulse moves from the bar to the number. |
| *new* `StatusLine.tsx` | §4.5. Pinned under the composer, never wraps above `900px`. Takes `activity`, not the problem. |
| *new* `hooks/useActivity.ts` | The word pools and the 3.5s rotation. Pools are data — edit them, do not add states casually. |
| *new* `Spinner.tsx` | The cycling `✻`, shared by the status line and the notes block. Static under reduced motion. |
| `EditorPane.tsx` | Gains the composer border, `--color-surface` fill and the `>` gutter mark; `ctrl+enter` runs. |
| `index.css` | Token values from §3, focus-mode rules, reveal and pulse keyframes, reduced-motion guard, **thin/faint scrollbar styling** — the native scrollbar is bright and reads as exactly the bolted-on chrome §2 forbids. |
| `hooks/useTypingFocus.ts` | Unchanged. The single `typing` boolean behind focus mode. |
| *new* `lib/format.ts` | `clock()` and `compact()` — the `1.2k` rule lives in one place, since the meta lines and the status line must agree. |
| *new* `TypedText.tsx` + `hooks/useTypewriter.ts` | §4.6. The hook owns the pacing and the reduced-motion escape; the component owns the caret and the `sr-only` full text. |

The counters behind §5 are accumulated **client-side** in `App`, not read from the server's metrics:
telemetry flushes every 1.5s, and a status line that lags your typing by a second and a half looks
broken. `useTelemetry`'s returned `metrics` is now unused by the UI for this reason — it is still the
server's own view, and the report card should use that one.

`App` re-renders on every keystroke as a result, so `EditorPane` is wrapped in `memo`. If a future
change gives it an unstable prop, that render cost comes back and Monaco is the thing that pays it.

One thing from the old rail that must not get lost in the move: `run` has to stay reachable without
scrolling. It is a key hint in a pinned status line now, so this is satisfied by construction — a
`run` button that can scroll out of reach is a bug found mid-demo, which is exactly how it was found.

The old rail's test readout has deliberately **not** kept a home. `lib/runTests.ts` still returns the
full `LocalRunResult`; `App` forwards it to `/api/sessions/{id}/run` and drops it on the floor. If a
future change needs the verdict on screen, that is a §4.7 decision, not a component decision.

---

## 12. The one-line test

If a stranger glances at the screen for two seconds, they should think *"something is keeping a log of
this person"* — not *"this is a coding website."* If a change makes the second reading more likely, it
is the wrong change.
