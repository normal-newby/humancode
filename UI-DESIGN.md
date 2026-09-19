# HumanCode — UI Design

The interface has one job: make it feel like **you are the model and there is a human on the other
side of the glass, prompting you and losing patience.** Every decision below follows from that.

Reference point is **Claude Code**, not an IDE and not a practice site — with the roles swapped. In a
coding agent's terminal the human types `>` and the machine answers under a `⏺`, stamping what the
answer cost underneath. Here the human on the other side sends the `>` prompts, and the `⏺` blocks
are yours: your code is the output being generated, your keystrokes are the tokens, and the footer
counts them while someone waits.

That swap is the whole product. It makes three things literal that were only jokes before: the meta
line is a usage stamp on your own output, the footer spinner narrates the thing generating text
(which is you), and a heckle arriving mid-keystroke is an **interrupt** — the most recognisable
artifact in the reference, and the best beat in the app.

We borrow the grammar, never the branding. No vendor logo, no vendor wordmark, no vendor model names
in the chrome. The accent below is ours.

---

## 1. The inversion, stated as layout

In a normal coding site, the problem is a panel and you are a panel — two peers in a split view. That
framing is wrong here. So:

- **One centred column.** No rails, no split panes, no dividers, nothing docked to an edge.
- **`>` lines are theirs.** The problem, every heckle. The first one is pinned at the top, because it
  is what you are still being asked.
- **`⏺` blocks are yours.** Your turn in progress *is* the editor; closed turns collapse to the tool
  call they amounted to — `Write(twoSum.js)` — plus what it cost.
- **Every closed turn carries a dim meta line** — elapsed time first, then characters written and
  deleted, formatted exactly like a token-usage readout. §5 is the whole spec.
- **Their caret blinks before they speak** (§4.4), at the point in the log where the prompt will land.
- **One footer** carries the rest: what you are doing, the session total, their patience, key hints.

```
   > two sum. given an array of integers and a target, return
     the indices of the two numbers that add to it. in any order.
   ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
   ⏺ Write(twoSum.js)
     ⎿  01:38 · ↑ 214 · ↓ 12
   > nested loop. bold choice for an array this size.
   ⏺ Write(twoSum.js)
     ⎿  00:47 · ↑ 96 · ↓ 140
     ⎿  Interrupted by user
   > still with me? that cursor hasn't moved in a wh▍     ← revealing, §4.6
     ⎿  staring at line 12 for 94 seconds                 ← their note
   ⏺ (no output)
     ⎿  03:55 · ↑ 0 · ↓ 0 · idle 47s
   > ▍                                                    ← composing, §4.4
                                                            ← log scrolls
   ⏺ Write(twoSum.js)                                     ← your live turn
     12  function twoSum(nums, target) {
     13    const seen = new Map();
     14    for (let i = 0; i < nums.length; i++) {
     ⎿  00:12 · ↑ 84 · ↓ 3                                ← live, this turn
   ✻ writing… (04:12 · ↑1.2k ↓431 ⧉2)   human impatience 34% ▓▓▓░░░   ⏎ submit  ^d end  esc to interrupt
```

Column is `max-width: 84ch`, centred. The live turn and the footer are pinned to the bottom of the
viewport and the log scrolls behind them — a terminal keeps the output still being written at the
bottom, and everything finished above it. Markers sit in a `1.25rem` left column so `>` and `⏺` line
up all the way down; that single alignment is most of why the screen reads as one surface.

**The log keeps its scrollback.** A meta line is a stamp on a turn, and it only means anything if the
turn it stamps stays on screen. Closed turns and answered prompts drop to `--color-faint` so their
newest prompt is unmistakably the live one.

---

## 2. Anti-goals — things that would make this look like LeetCode

Call these out in review. Any one of them collapses the whole concept back into a practice site.

| Do not | Instead |
|---|---|
| Split pane with a draggable divider | One column, no resize handles, nothing docked to an edge |
| Bordered cards with rounded corners around every panel | **No borders anywhere.** Zones are separated by whitespace and contrast alone |
| Green/yellow/red difficulty pills | Lowercase plain text: `easy`, `medium`, `hard`, in `--color-sub` |
| Tabs: Description / Solutions / Submissions | There is one view. There are no tabs anywhere |
| A console drawer that slides up from the bottom | There is no test output on screen at all. The interviewer tells you — §4.7 |
| A pass count, a failure list, a green check anywhere in the session | The run result is a signal for the model, never a readout for the candidate |
| Dense icon toolbars | Key hints in the footer — `⏎ submit`, `^d end`. No filled buttons, no icons |
| A big green "Accepted" banner | They say something begrudging in their next prompt. That is the reward |
| Chat bubbles, avatars, speech tails, alternating alignment | Flush-left log lines under a `>` or `⏺` marker. Nobody's terminal has bubbles |
| An input box around the editor | The editor is a `⏺` block, not a composer. A box makes you the user again and undoes the premise |
| Emoji anywhere in the chrome | The glyph set in §4.1 and nothing else |

The general rule: **if it looks like a tool, it is wrong. It should look like an agent session someone
else is running, and you are the agent.**

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
  --color-surface: #24231f;  /* meter track mask, scrollbars */

  /* text */
  --color-ink:     #e6e2d8;  /* live turn, code */
  --color-sub:     #8a857a;  /* meta lines, status line, notes */
  --color-faint:   #4b4841;  /* past turns, glyphs, the one border */

  /* accent — every caret, their newest `>`, your live `⏺` */
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

Lowercase everything, including their prompts. `submit`, `end`, `idle 47s`. Never Title Case, never
ALL CAPS. A terminal does not shout. The two exceptions are the log's own vocabulary, which is quoted
from the reference and must stay recognisable: `Write(twoSum.js)` and `Interrupted by user`.

---

## 4. Zones

### 4.1 Glyphs

The whole vocabulary. Adding to this list is a design change, not an implementation detail.

| Glyph | Code point | Means |
|---|---|---|
| `>` | U+003E | **their** prompt |
| `⏺` | U+23FA | **your** turn — the live one, or a closed one |
| `⎿` | U+23BF | a receipt or an aside, attached to the block above |
| `✻` | U+273B | the footer spinner: you, generating |
| `↑` `↓` | U+2191 / U+2193 | characters written / deleted |
| `⧉` | U+29C9 | a paste |
| `▍` | U+258D | someone is mid-sentence (§4.4, §4.6) |

All glyphs are `--color-faint` and `aria-hidden` — they are texture, never the only carrier of
meaning (§10). Three exceptions, all `--color-accent`: the `⏺` on your live turn, the `>` on their
newest prompt, and every `▍` caret. With Monaco's own caret those are the only accented things on
screen — which means the accent always marks *whoever is currently producing text*.

### 4.2 The log

Flush-left, `max-width: 84ch`, `1.25rem` between blocks. Every block is a marker in a `1.25rem` left
column and its content hanging beside it, so `>` and `⏺` align all the way down.

**Their standing prompt is pinned** to the top of the scroll container, at `--color-ink` even after
everything else has dimmed. It is what you are still being asked and it does not go away. A `┄`
hairline in `--color-faint` sits under it so it reads as pinned rather than as the newest line.

Below it the log alternates, the way an agent transcript does:

| Block | Renders as |
|---|---|
| their prompt | `>` + the line, typed out (§4.6), with their notes as `⎿` asides under it |
| your closed turn | `⏺ Write(twoSum.js)` + the meta line, plus `⎿ Interrupted by user` when they cut in |
| your closed turn, nothing typed | `⏺ (no output)` + the meta line carrying `idle 47s` |

`(no output)` is not a joke at the candidate's expense — it is what the log genuinely has to say about
a turn in which nothing was generated, and it is the sentence a model's transcript would carry. It
lands harder than any insult the interviewer could write.

Everything scrolls, auto-stuck to the bottom unless the candidate has scrolled up. Blocks older than
their newest prompt are `--color-faint`. Nothing is ever removed — the log is the point — but cap the
DOM at the last 50 blocks; the report card owns the full history.

A muted `canned` marker (9px, `--color-faint`) sits under a prompt that came from the fallback rather
than the model. Useful in dev, invisible enough for a demo.

No test verdict ever lands here — see §4.7.

### 4.3 Your turn, live

**Turns are the spine of the log, and `submit` is what closes one.** That single rule is what makes
the swap real rather than cosmetic: you generate, you hand back, they respond, they prompt again —
the interview loop and the agent loop are the same loop.

A turn closes when:

| Event | Closes with |
|---|---|
| `⏎ submit` | a plain stamp — you handed it over |
| their prompt, while you were typing | `⎿ Interrupted by user` |
| their prompt, while you were idle | a stamp carrying `idle 47s` |

Every prompt closes a turn, because a user message ends an assistant's turn — but only the ones that
land mid-keystroke are *interrupts*. You cannot interrupt someone who was not talking, and pretending
otherwise would spend the best artifact in the app on nothing.

The turn in progress is the editor: Monaco, headed by `⏺ Write(app.js)`, stamped underneath with a
live meta line counting **this turn only**. It is pinned to the bottom of the viewport because that is
where a terminal keeps the output still being written. A problem with more than one file gets a file
navigator between the header and the editor — see §4.3a.

- **No border and no fill.** Monaco's background and gutter match `--color-canvas` exactly. A box
  would make it an input again and hand the model role back to the interviewer.
- Stripped: no minimap, no folding, no glyph margin, no overview ruler,
  `renderLineHighlight: 'none'`.
- **Line numbers stay on**, in `--color-faint`. They are the one piece of IDE furniture that earns its
  place: their notes refer to them ("staring at line 12"), and that joke needs the reader to be able
  to look.
- Caret is `--color-accent` and does not blink while typing. Theirs blinks; yours does not.
- `ctrl+enter` submits.

### 4.3a The file navigator

Problems are however many files they need — an HTML/CSS/JS scaffold for something visual, one file
for something simpler — decided once when the problem was authored or generated, never mid-session.
When there is more than one, a row of filenames sits between the `Write(...)` header and the editor:

```
⏺ Write(app.js)
  index.html   styles.css   app.js
     12  function addTodo() {
     13    const input = document.getElementById('new-todo');
  ⎿  00:12 · ↑ 84 · ↓ 3
```

This is a narrower case than the tabs §2 forbids — that rule is about *content* tabs (Description /
Solutions / Submissions), a different view of the same problem. A file navigator switches which file
of your own output is on screen, which is closer to the `Write(...)` header itself than to a tab bar.
Still, it stays inside the vocabulary §2 sets rather than importing IDE furniture wholesale:

- **No borders, no pills, no icons.** Plain lowercase filenames, separated by whitespace — texture,
  not chrome.
- The active file is `--color-accent`, the same way the live `⏺` and their newest `>` are — accent
  always marks whoever is currently producing output, and here that is whichever file you are looking
  at. Inactive filenames are `--color-faint`, `--color-sub` on hover.
- Switching files is a view change, not a new turn. It does not touch the meta line, the turn
  boundary, or the diff baseline — only what you type does that.
- A single-file problem shows no navigator at all. It would be one inert, always-accent label doing
  no work.

The closed turn's `Write(...)` label reflects what was actually touched that turn, not what was
merely viewed: one file names itself, a few name themselves, more than a couple collapses to a count
(`Write(3 files)`) rather than crowding the log with a file listing.

### 4.3b The preview

Problems are small apps now (CLAUDE.md §6), so the last entry in the navigator row is not a file:

```
⏺ Write(3 files)
  index.html   styles.css   app.js    preview
```

Selecting it puts the candidate's own page where the editor was. **It is a swap, not a split** —
§2 bans the draggable divider and the docked panel, and a 38vh slot cannot honestly hold both a
page and an editor anyway. You are looking at your code or you are looking at what it does, which
is what anyone does on one screen.

**This is not §4.7 leaking.** That rule is about a *verdict* — a pass count, a failure list, a
green check — and none of those exist here or anywhere. A preview shows what you built, not how
you scored: it answers "does the button toggle" and says nothing whatsoever about whether the
interviewer thinks you are doing well. The candidate still finds that out by being told. If a
future change ever renders an assertion result into this frame, it has stopped being a preview and
§4.7 applies to it.

**It rebuilds when you switch to it, and there is no refresh button.** The two views share one
slot, so the buffers cannot change while the page is the thing on screen — there is no such thing
as a stale preview here, and a debounce watching for one would never fire. What the candidate is
promised is only that what they switch to is current.

Rules that keep it inside §2:

- **The white rectangle is the page's own**, not a card. No border, no radius, no shadow, no
  chrome of ours around it — the frame is flush in the slot the editor occupies.
- `preview` sits in the same row as the filenames, in the same plain lowercase, accent when active
  and `--color-faint` when not, because it is another way to look at your own output. A wider gap
  is the only thing marking it as not-a-file. No icon, no play button, no separator glyph.
- **A problem with no HTML file shows no preview entry**, the same way a single-file problem shows
  no navigator. There would be nothing behind it.

The frame is `sandbox="allow-scripts"` with **no** `allow-same-origin`. Omitting the second flag is
the whole protection: the page gets an opaque origin and cannot reach the session, the parent
document or storage. Adding it back would look like a fix the first time a candidate's
`localStorage` call throws in there; it is not one.

Only files the HTML actually references are inlined. Delete the `<script src="app.js">` tag and the
preview stops running the script — because that is what the page now does, and a preview that
disagrees with the page the rubric is judged against is worse than a blank screen.

### 4.4 Their caret

Before a prompt lands, a `>` and a blinking `▍` appear at the tail of the log — for `850ms`, at the
exact point where the prompt will appear.

The SSE channel delivers a finished utterance, so this delay is manufactured: the line is held back
and the caret shown in its place. That is deliberate. Watching someone compose a message at you is
worse than reading it, and it is the only moment in the session where you know something is coming
and can do nothing about it. It is the same instinct as the "weaponized silence" note in CLAUDE.md,
paid for in 850ms.

Their caret blinks at `1.1s`, `steps(1, end)` — a hard terminal blink, not a fade. Yours never blinks.
The asymmetry is the tell.

### 4.5 The footer

One line under your live turn, 13px `--color-sub`, wrapping only below `900px`, key hints
right-aligned. It is the heaviest of the small type in the app on purpose: it is the last thing on
screen, and at 11px it read as a footer rather than as instrumentation.

It takes the shape a coding agent's footer takes — spinner, what is happening, and the cost so far in
one parenthesis:

```
✻ writing… (04:12 · ↑1.2k ↓431 ⧉2)   human impatience 34% ▓▓▓░░░   ⏎ submit  ^d end  esc to interrupt
```

| Segment | Content |
|---|---|
| activity | the cycling `✻`, one lowercase word for what you are doing, then `(clock · totals)` |
| impatience | §6 — **theirs**, and labelled as such |
| hints | `⏎ submit`, `^d end`, and the tell |

**The problem's title and difficulty are not here, and are nowhere.** Their prompt said what the
problem is; repeating `two sum · easy` for the whole session is a label on a product. What replaces
it moves.

**The activity word** narrates *you*, which after the swap is simply correct: the spinner in a
terminal agent describes the thing generating output, and that is now the candidate.

| State | Pool |
|---|---|
| typing | `writing`, `typing`, `scribbling`, `composing`, `hacking`, `cranking` |
| idle | `thinking`, `contemplating`, `pondering`, `deliberating`, `ruminating`, `mulling` |
| idle, impatience ≥ 60 | `stalling`, `hesitating`, `stewing`, `wavering`, `reconsidering` |
| submitting | `submitting`, `handing over`, `waiting` |

It re-picks the instant the state changes and then every 3.5s while the state holds, never twice in a
row on the same word. The second idle pool is the meter leaking into the language: past 60 it stops
being generous about the pause, and it says so without them having to spend a prompt on it.

Adding words is free and welcome; adding a *state* is not — each one needs a signal actually
distinguishable from the other three, or the word stops meaning anything.

The clock is driven by a local 1s interval, **not** by telemetry: telemetry only flushes when there
are events, so a server-derived clock stalls the moment you stop typing — exactly when the clock
matters most. The parenthesised numbers are **session totals**; everything in the log is a per-turn
delta of them.

**`⏎ submit`**, not "run". Pressing it hands the turn back (§4.3); that it also runs the tests is an
implementation detail the candidate never sees (§4.7). `ctrl+enter` in the editor, and a real click
target too, because a judge at a demo table will reach for the mouse.

**`^d` ends the session, on the second press.** The hint flips to `^d again to end` in `--color-hot`
for three seconds, then disarms. `^c` would be the more idiomatic terminal exit, but it is copy, and a
candidate copying a line must not end their interview. `^d` is EOF, which is the right verb anyway.

**`esc to interrupt` is the tell, and it is not yours.** In a terminal that hint belongs to whoever is
waiting on the model. Here that is the human on the other side, so the hint sits in `--color-faint`
and does nothing when you press it — except flash `esc is theirs` for two seconds, which is the
cheapest way to teach the premise. Do not wire esc to anything; the moment a candidate discovers the
key does not belong to them is the moment the whole layout clicks.

Worked examples (`in [2,7,11,15], 9` / `out [0,1]`) are **not** here, and they are not under their
prompt either — they are not anywhere. See §4.7.

### 4.6 They type

**Every prompt they send is revealed a character at a time**, including the first one. Nothing they
say ever appears all at once.

This is not ornament. A line that pops into existence is a notification; a line that types is a person
composing a thought at you, and you read it at their pace, unable to skip ahead. It also covers the
model's latency with something that looks intentional. Together with §4.4's caret, a heckle takes
850ms of dread plus ~2.8s of delivery — call it four seconds where all you can do is watch someone
type at you.

- A `▍` block caret in `--color-accent` trails the text and disappears on the last character.
- Long lines reveal **several characters per tick rather than typing slower** — the reveal is paced to
  finish in ~2.8s whether it is a three-sentence problem statement or a six-word insult. A statement
  that takes twelve seconds to deliver is not a joke, it is a loading screen.
- **Their notes wait for the prompt to finish.** §8's 300ms delay applies from that point, not from
  arrival: the aside lands after the sentence, never during it.
- The log keeps following the growing line, but only if the candidate was already at the bottom.
  Scrolling up to re-read must not get yanked back.
- Under `prefers-reduced-motion` every line is simply there.

Only prompts that arrive while you are watching type. One already on screen re-renders whole —
nothing re-types on a React re-render, which would be a nasty flicker every keystroke.

Your own turns do not type. You are typing them.

### 4.7 Test results are never shown

The candidate presses `submit`. Their turn closes, and some seconds later the human answers. **They
never see a pass count, a failure list, a green check or a red cross.**

Nor do they see **worked examples**. An `in [2,7,11,15], 9` / `out [0,1]` pair is a test case with
better manners: it hands over a case the candidate can eyeball their way to, and it turns the opening
turn into a spec sheet instead of a question someone just asked you. Their prompt carries the problem
in prose, the way it would be said out loud, and that is all.

`Problem` never carried worked examples at all once the schema moved to files instead of a single
function with test cases (CLAUDE.md §6) — there is no `examples` field left to accidentally leak.
The lesson stands as the reason not to add one back: an example is a spec sheet, and the whole point
is that their prompt is a sentence, not a sheet.

There is no automated result to send anywhere now either — verification is the interviewer reading
the diff against the rubric (CLAUDE.md §6), the same judgment call it always made, just without a
pass/fail signal feeding it first. `SUBMITTED` is what the interviewer reasons from. Finding out how
you did by reading the interviewer's face is the entire product. The moment a `4/6` appears on
screen, the candidate reads the number, ignores the sentence, and you have built LeetCode with a
mascot.

What the candidate *does* get is the footer word turning to `submitting…` and the hint reading
`submitting…`. That is feedback that the button worked, not a verdict.

This is also why submitting stays cheap to press: it is asking how you are doing, and being answered
in words.

---

### 4.8 What they ask you for, before it starts

The start screen is the only place a difficulty word appears. One question above the `begin` link:

```
how hard should this be
easy   medium   hard
```

The legend is `--color-faint`, the three words are `--color-sub`, and the chosen one is
`--color-accent` and underlined. Nothing else changes — no pill, no fill, no dot, no border, and no
colour that means anything. §2 bans the green/yellow/red difficulty badge by name because it is the
single most LeetCode thing a screen can wear, and a picker is exactly where it would come back in.

**It is three radios under the hood**, visually hidden inside their labels, so arrow keys move between
them and a screen reader is handed one labelled group rather than three unrelated buttons (§10). The
whole `fieldset` disables while the session is starting, and the words drop to 40% — the same
disabled treatment the `begin` link uses.

`medium` is preselected. A default of "any" would be more honest about what the server does with a
null, but the picker is a question being asked of the candidate, and a question with nothing chosen
reads as a form to fill in rather than an interviewer's opening.

**The choice does not follow them into the session.** It rides on `POST /api/sessions`, and after
that the word is gone: the footer never shows it (§5) and no prompt repeats it. What they chose is
visible only in how hard the thing they were handed turns out to be.

The picker keeps its value across sessions, so ending one with `^d` and starting another lands you on
the same level without re-answering. Raising the stakes should be a deliberate click.

---

## 5. The meta line

The centrepiece, and the thing most likely to get watered down by someone who reads it as decoration.
It is the product's thesis in one row: **while you were quiet, it was counting.**

It hangs under a closed turn — under **your** output, which is what makes it a usage stamp rather
than a timestamp. Indented to align with the block's first character:

```
⎿  04:12 · ↑ 412 · ↓ 180 · ⧉ 1 · idle 22s
```

Rules:

1. **Time first, always.** How long the turn took — `mm:ss`, tabular. It is the one segment never
   omitted, because "how long have I been at this" is the pressure the whole product runs on.
2. **`↑` written, `↓` deleted** — characters, not tokens, but formatted like a token readout:
   thousands as `1.2k`, one decimal, no unit word. The resemblance is the joke and it is also honest;
   these are the units that actually matter to a person typing.
3. **`⧉` pastes**, count only.
4. **`idle Ns`** appears only when the prompt that closed the turn was an idle trigger. It is the
   receipt for a turn in which you produced nothing.
5. **Segments with a zero value are omitted** — except on an idle turn, where `↑ 0 · ↓ 0` is the point.
6. Separator is ` · ` in `--color-faint`. Numbers are `--color-sub`, tabular, never bold.

**The numbers are per-turn deltas — what that turn cost — not running totals.** This is the direct
analogue of a per-message token count. Session totals live in the footer (§4.5), the way a context
readout does. Getting this backwards makes every meta line a near-duplicate of the one above it and
the row stops carrying information.

**One exception: the live turn's meta line ticks.** Under the editor it counts the turn in progress,
second by second, and freezes into the log the moment the turn closes. That contrast — one clock
running, all the others stopped — is what makes the log read as a transcript instead of a dashboard.

Sourcing: all four values are accumulated client-side from the telemetry already being batched
(`written`, `deleted`, `pastes`). **Do not add a round-trip for this.**

---

## 6. Human impatience meter

The one piece of colour semantics in the app, so it must be unambiguous: **green is calm, red is
furious.** It lives inline in the footer, because a terminal status line is exactly where a
percentage-with-a-bar belongs.

**The label says `human impatience`, and the word `human` is load-bearing.** Everything else in the
footer is about you; an unlabelled meter in this layout reads as something about the model — its
confidence, its budget — which is the opposite of what it is. It is the patience of the person waiting
on your output, and it is the only thing on screen that belongs entirely to them.

- Label `human impatience`, then the number, then the bar. No `/100`.
- Bar is `96px` wide, `8px` tall, `border-radius: 9999px`, vertically centred on the text.
- The **gradient lives on the track itself**, always at full width.
- A `--color-surface` cover eats the unreached portion from the right.

The technique matters. If you put the gradient on a growing fill element, the fill renders
green-to-red at *every* value and the colour stops meaning anything. Masking from the right instead
keeps each position's colour fixed — at 20 you see green only, at 95 the bar has swept through amber
into red — and it needs no width measurement:

```tsx
<div className="relative h-2 w-24 overflow-hidden rounded-full"
     style={{ background: GRADIENT }}
     role="meter" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}
     aria-label="human impatience">
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
- Closed turns and answered prompts → `opacity: 0.25`
- Their pinned prompt → `opacity: 0.35`
- Footer → `opacity: 0.25`, **except the activity word**, which never dims
- Their newest prompt stays fully visible. They never dim. They are always watching.
- Your live turn never dims — you are looking at it.

**After 1.5s of silence**: everything returns to full opacity over 400ms.

The result: while you are in flow the screen is your own output and one line above it. The instant you
hesitate, the log and the counters fade back in and you remember that all of it was being written
down. That is the entire emotional design of the product in one transition.

The activity word is exempt for the same reason their newest prompt is: it is the read on you, not
instrumentation. Dimming it while typing would hide it in the one state it exists to report — you
would see `thinking…` forever and never once see `writing…`.

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
| Their caret (§4.4) | hard blink, `steps(1, end)` | 1.1s loop |
| Their caret, before a prompt | held before delivery | 850ms |
| New prompt or closed turn | fade + 4px rise | 200ms |
| Previous prompt dimming to faint | colour | 400ms |
| Meta line, their notes | fade, 300ms after the line above finishes | 200ms |
| Meter width | width | 700ms ease-out |
| Impatience number pulse (≥85) | opacity | 1.6s loop |
| Focus mode | opacity | 250ms out / 400ms in |

The receipt arriving a beat *after* the block it stamps is deliberate: you read the line, then the
receipt lands under it. Simultaneous, it is noise; delayed, it is a verdict.

All of it inside a `prefers-reduced-motion: reduce` guard that drops to instant state changes. The
pulse, the spinner and the line reveal in particular must not run for people who have asked for
reduced motion — the spinner falls back to a static `✻`, and lines simply appear. The activity word
still rotates: it is information, not animation.

---

## 9. Responsive

The single column is most of the responsive work already done. Below `900px`:

- Column goes to `100% - 2rem`. Nothing else changes structurally.
- The footer wraps to two rows — activity and totals on the first, impatience on the second. The key
  hints drop; there is a touch keyboard in the way of both of them anyway.
- The live turn keeps its height and stops growing.

Below `700px` this is a viewing experience, not a working one. Keep it legible; do not optimise it.

---

## 10. Accessibility

- All text meets 4.5:1 against `--color-canvas` **except** `--color-faint`, which carries closed
  blocks and glyphs. Those are still fully legible in the report card, and no glyph carries meaning on
  its own.
- **Every glyph is `aria-hidden`** with an `sr-only` word beside it. `↑ 412` announces as
  "412 characters written", not "up arrow 412". This is the easiest thing here to get wrong.
- The meta line is a `<dl>`-shaped structure semantically or, failing that, one `aria-label` on the
  whole row. Do not ship it as a bare string of glyphs and numbers.
- The meter is `role="meter"` with `aria-valuenow/min/max` and a label. The colour is redundant with
  the number, so it is never the sole carrier of meaning.
- New prompts go in an `aria-live="polite"` region. Meta lines and notes do **not** — they would
  interrupt constantly, and the joke is that they are peripheral.
- Their caret is decorative: `aria-hidden`, with an `sr-only` "the interviewer is typing" beside it.
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

| Component | Role |
|---|---|
| `App.tsx` | Owns the log, the turn boundaries, the prompt queue, the `typing` flag and the local clock. One centred column; log scrolls, live turn + footer pinned. |
| `Transcript.tsx` | §4.2. Their pinned prompt, then alternating `>` prompts and `⏺` closed turns. Holds `Prompt`, `Turn`, `Block` and `Result` internally. |
| *new* `LiveTurn.tsx` | §4.3. `⏺ Write(file)` + the editor + the ticking meta line. The only place the editor is mounted. |
| *new* `TypingIndicator.tsx` | §4.4. Their `>` and a blinking `▍` at the tail of the log. |
| `PreviewPane.tsx` + `lib/buildPreview.ts` | §4.3b. The candidate's page in a sandboxed frame; the lib assembles the files into one document. |
| `MetaLine.tsx` | §5. Per-turn deltas, frozen when the turn closes; the `live` variant counts the turn in progress. |
| `StatusLine.tsx` | §4.5. `✻ word… (clock · totals)`, the meter, `⏎ submit`, `^d end`, and the `esc` tell. |
| `DifficultyPicker.tsx` | §4.8. Three lowercase words on the start screen, radios under the hood. The only difficulty word in the app. |
| `ImpatienceMeter.tsx` | §6. Masked gradient, `96×8`, labelled `human impatience`; pulse on the number. |
| `EditorPane.tsx` | Monaco with no border and no fill, background matched to `--color-canvas`. `ctrl+enter` submits. One model per file (§4.3a), swapped via `setModel`; owns the file navigator row itself. |
| `TypedText.tsx` + `hooks/useTypewriter.ts` | §4.6. The hook owns pacing and the reduced-motion escape; the component owns the caret and the `sr-only` full text. |
| `hooks/useActivity.ts` | The word pools and the 3.5s rotation. Pools are data — edit them, do not add states casually. |
| `Spinner.tsx` | The cycling `✻`. Static under reduced motion. |
| `hooks/useTypingFocus.ts` | The single `typing` boolean behind focus mode — and the same boolean decides whether a prompt counts as an interrupt. |
| `lib/format.ts` | `clock()` and `compact()` — the `1.2k` rule lives in one place, since the meta lines and the footer must agree. |
| `index.css` | Tokens from §3, focus-mode rules, reveal/blink/pulse keyframes, reduced-motion guard, **thin/faint scrollbar styling** — the native scrollbar is bright and reads as exactly the bolted-on chrome §2 forbids. |

Removed by the swap: `VoiceBand.tsx`, `LeftRail.tsx`, `NotesPanel.tsx`, `NotesBlock.tsx`. Their notes
live under their prompts now, and there is no rail and no voice band left to put anything in.

**Turn bookkeeping lives in `App` and nowhere else.** `turnBase` holds the counters and clock reading
at the moment the current turn opened; `closeTurn(interrupted, idleSeconds)` diffs against it, pushes
the closed block, and re-bases. Both a submit and an arriving prompt go through that one function —
if a third thing ever closes a turn, it goes through it too.

**The prompt queue is keyed on the queue alone.** A second prompt arriving during the 850ms blink
re-arms the timer for the one already waiting rather than cancelling it; guarding that effect on
`composing` deadlocks it, which is a bug worth not writing twice.

The counters behind §5 are accumulated **client-side**, not read from the server's metrics: telemetry
flushes every 1.5s, and a footer that lags your typing by a second and a half looks broken.
`useTelemetry`'s returned `metrics` is unused by the UI for this reason — it is still the server's own
view, and the report card should use that one.

`App` re-renders on every keystroke as a result, so `EditorPane` is wrapped in `memo`. If a future
change gives it an unstable prop, that render cost comes back and Monaco is the thing that pays it.

There is no local runner and nothing to drop on the floor anymore — `submit` POSTs to
`/api/sessions/{id}/submit` with no body, and the interviewer's judgment never reaches the client at
all (CLAUDE.md §6). If a future change needs a verdict on screen, that is a §4.7 decision, not a
component decision — and it would mean putting one back, not surfacing one that already exists.

---

## 12. The one-line test

If a stranger glances at the screen for two seconds, they should think *"an agent is working, and
someone impatient is watching it"* — and only then realise the agent is a person. Not *"this is a
coding website."* If a change makes the second reading more likely, it is the wrong change.
