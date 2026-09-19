# HumanCode

An inverted technical interview. **The AI asks the question, you write the code.** A smug AI interviewer
watches you type in real time, heckles you when you wander off the optimal path, nags you when you idle,
drops hints when you beg, and hands you a report card at the end.

The pitch is not "a realistic inverse interview." It's a *new interview dynamic* that happens to look like
the real thing — the comedy and the pressure are the product.

---

## 1. Product shape

Three tiers, in build order. Do not start a tier until the one above it demos end to end.

**Core (must ship)**
- Problem delivery from a curated bank; AI states the problem conversationally.
- Monaco code editor with live capture — every edit streams to the backend.
- Reactive commentary: the AI reacts to what you're actually typing.
- AI chat panel (the interviewer's voice) + pressure/impatience meter UI.
- Post-session report card: verdict, insults, begrudging compliments, similar problems.

**Fun (the demo wins here)**
- Behavioral tracking: idle time, time-to-first-keystroke, delete/rewrite ratio, paste bursts.
- Impatience meter that actually rises and changes the AI's tone.
- Interviewer avatar whose expression escalates with impatience (use an **original mascot**, not
  a real vendor's logo — an angry-eyebrows Claude Code or OpenAI mark reads as an official product,
  which is a headache you do not need at a demo table).
- Mid-task curveballs: "actually, make the button yellow instead of green", "now show a count of
  what's left".

**Stretch**
- Passive mode: tab stays open, app pings you at random and demands you solve something.
- AI-generated problems; difficulty adapting to measured skill.
- Cheating detection (see §7 — paste telemetry first, GPTZero second).

### High-value ideas not yet on the list

- **Interviewer's private notes panel.** A sidebar where the AI visibly "takes notes" on you:
  *"Candidate has been staring at line 12 for 94 seconds."* / *"Declared a HashMap. Some hope."*
  Nearly free — it's the trigger digest rendered as text — and it is the funniest thing on screen.
- **Session replay in the report card.** You already log every edit; scrubbing the replay costs almost
  nothing and is a killer closing beat for a demo.
- **Complexity interrogation.** At a random moment the AI demands the time complexity of what you've
  written so far and grades the answer. Cheap, very on-theme.
- **Confidence betting.** Before you hit Run, the AI makes you predict pass/fail. Losing a bet spikes
  the impatience meter.
- **Follow-up phase.** After you pass: *"Great. Now what if the input were already sorted?"* Matches
  real interviews and reuses the whole pipeline.
- **Weaponized silence.** On idle, the AI says nothing for an uncomfortably long beat, then lands one
  devastating line. Comedic timing is a deliberate delay in the trigger engine, not a model behavior.

---

## 2. The one architectural rule

**Never put the model in the loop of keystrokes.**

Keystrokes → deterministic Java trigger engine → *occasionally* → the model.

The backend owns a `SessionState` (current code snapshot, rolling metrics, recent event digest, impatience
score). A plain Java `TriggerEngine` decides **when** the interviewer speaks. The model only decides **what** it
says. That keeps cost bounded, latency predictable, and the demo rehearsable — and it's what makes prompt
caching work, because the model sees a stable prefix plus a small volatile suffix.

If you find yourself calling the API on every edit, stop and fix the trigger engine.

### What actually costs money

There is exactly one `@Scheduled` in the app: `InterviewDirector.tick()`, every 2s. That tick is local
and cheap — it reads in-memory state and almost always does nothing. A model call needs three guards to
pass:

1. a rule in `TriggerEngine` fires;
2. `sse.isConnected(sessionId)` — **no listener, no call**, so a closed tab costs nothing;
3. the `quip-cooldown` (15s) has elapsed — *unless the trigger is `immediate`*.

**`PASTE_BURST`, `FIRST_IMPLEMENTATION` and `CURVEBALL` are `immediate` and bypass the cooldown
entirely.** That is deliberate for the first two — a delayed reaction to a paste feels broken, and
the interviewer catching it *as it happens* is the joke. `CURVEBALL` is immediate for a different
reason: it is a one-shot, timer-gated event, not a reaction to typing, so there is nothing to
protect it from — see below, it costs no model call either way. `SUBMITTED` (what `TESTS_PASSED`/
`TESTS_FAILED` used to be, before there was anything to run) respects the cooldown normally, so
hammering the submit button while debugging does not generate one call per press.

With `humancode.problems.source=generated` (the `prod` profile) there is **one generation call per
session started**, but it no longer happens in front of the user: `ProblemPool` keeps a couple warm and
replaces what is taken in the background. See §6.

**`CURVEBALL` costs nothing, ever.** The text is pre-authored (by the problem's `curveballs` list,
written by whoever authored or generated the problem), the same trust level as the opening problem
statement — also delivered verbatim, never paraphrased by a model call. `Interviewer.react()`
special-cases `Trigger.Kind.CURVEBALL` at the top and returns the trigger's own text directly,
before the client-availability check. No API call, no guard, no fallback. If you ever find yourself
wiring a model call into curveball delivery, stop — the entire point was that the model decides
*what* only when it has to, and here it never has to.

---

## 3. Stack

**Before touching anything visual, read [UI-DESIGN.md](UI-DESIGN.md).** It is the binding spec for
layout, palette, motion and the anti-LeetCode rules. The short version: **you are the model.** A
Codex CLI-style log with the roles swapped — `▌` lines are the human on the other side prompting you,
`•` blocks are your output, your live turn is the editor, `submit` closes a turn, a heckle arriving
mid-keystroke renders as `└ Interrupted by user`, and the footer counts your keystrokes like tokens
while their impatience meter fills. One centred column under one `openai codex` window tab; no rails,
split panes or borders.

The app is **dressed as Codex on purpose** — it is being shown to an OpenAI sponsor. UI-DESIGN.md §0
draws the line: borrow the grammar and name the CLI, never claim to be it. No OpenAI logo, no real
model id anywhere that reads as configuration rather than a punchline, no suggestion that anything on
screen came from an OpenAI model. `model: you` is both the joke and the disclaimer.

| Layer | Choice |
|---|---|
| Backend | Spring Boot 4.1.1, Java 25, Maven (`./mvnw`) |
| Persistence | Spring Data JPA + SQLite (`org.xerial:sqlite-jdbc`) |
| AI | OpenAI, official `com.openai:openai-java` SDK |
| Frontend | React 19 + TypeScript + Vite 8 (`.tsx`), `@monaco-editor/react`, Tailwind 4 |
| Client→server | REST, telemetry batched ~1.5s |
| Server→client | SSE (`SseEmitter`), one stream per session |
| Code execution | **None** — verification is judgment, not a runner (see §6) |

### Dependencies

Already wired in `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-validation`,
`spring-boot-starter-data-jpa`, `sqlite-jdbc`, `hibernate-community-dialects`, `com.openai:openai-java`
(pinned via the `openai-java.version` property), Lombok.

Three notes worth keeping:
- SQLite's Hibernate dialect (`org.hibernate.community.dialect.SQLiteDialect`) ships in
  `hibernate-community-dialects`, **not** in the JDBC driver. Its version is managed by the Spring Boot
  BOM, so do not pin it.
- SQLite is single-writer. `spring.datasource.hikari.maximum-pool-size=1` is deliberate — raising it
  buys nothing and earns intermittent `SQLITE_BUSY` under the telemetry write path.
- **Renaming a constant in `EventType` or `Phase` breaks every existing database**, and
  `SqliteSchemaMigrator` is why it no longer bites. Hibernate writes a
  `check (type in ('EDIT','PASTE',…))` listing the constants *as they were when the table was first
  created*; `ddl-auto=update` never rewrites it, and SQLite cannot drop a constraint. So the day
  `RUN` became `SUBMIT`, every telemetry insert threw `SQLITE_CONSTRAINT_CHECK` mid-session while a
  brand-new database worked perfectly. There is no escape at the mapping level — `columnDefinition`,
  `@JdbcTypeCode` and an `AttributeConverter` were all tried, and Hibernate emits the check from the
  attribute's Java type regardless — so the repair has to happen in the database, which is what the
  migrator does at startup: it rebuilds `telemetry_events` when the stored DDL is missing a value it
  should have.

  **The migrator only knows about the rename it was written for.** Its trigger is
  `sql.contains("'SUBMIT'")` and its rebuild hardcodes today's five constants, so the *next* enum
  change needs both updated, in `SqliteSchemaMigrator`, or the constraint silently goes stale again.
  `sessions.phase` has the same frozen check and no migration at all; `Phase` has not changed yet.

### Frontend wiring

Scaffolded in `web/` (Vite `react-ts` template — all components are `.tsx`).
Vite builds to `web/dist`; **no build output ever lands in `src/`.**

**The Maven build owns the frontend.** `pom.xml` wires two plugins:

| Phase | What runs |
|---|---|
| `generate-resources` | `frontend-maven-plugin` installs a pinned node/npm into `target/`, then runs `npm install` and `npm run build` in `web/` |
| `process-resources` | `maven-resources-plugin` copies `web/dist` → `target/classes/static` |

Both phases run ahead of packaging *and* ahead of `spring-boot:run`, so:

- `./mvnw package` → one runnable jar with the UI inside at `BOOT-INF/classes/static/`.
- `./mvnw spring-boot:run` → serves the real UI on `:8080`, no separate frontend step.
- `./mvnw package -Dskip.frontend=true` → backend only. Use this while iterating on Java; the frontend
  build adds seconds to every cycle and you rarely need it.

Node is **pinned** in `pom.xml` (`node.version` / `npm.version`) and installed into `target/node`, so the
build does not depend on what happens to be on anyone's `PATH`. A teammate with no node installed can
still run `./mvnw package`. Bump those properties rather than relying on a local upgrade.

**Day-to-day frontend work should still use the Vite dev server** — `npm run dev` in `web/` (`:5173`)
alongside `./mvnw spring-boot:run` (`:8080`). You want hot reload, not a Maven cycle. Vite proxies
`/api` → `:8080`, so the browser stays on one origin and **no CORS config is needed** on the Spring
side. Keep new endpoints under `/api`.

The Maven wiring is for producing a demo artifact, not for the inner loop.

---

## 4. Backend package layout

Under `com.example.humancode`:

```
interview/   Session lifecycle, SessionState, phase machine (INTRO → CODING → FOLLOWUP → REPORT)
telemetry/   Event ingest, metric computation, TriggerEngine
ai/          OpenAI client wrapper, PromptAssembler, structured reaction types
problem/     Problem bank (JSON resources), selection, generation
report/      End-of-session report card
web/         REST controllers, SSE hub, DTOs
```

Rules:
- Controllers are thin. All logic lives in the service layer.
- DTOs and value types are Java `record`s. Mutable entities use Lombok.
- `SessionState` lives in memory (a `ConcurrentHashMap` keyed by session id) and is *snapshotted* to
  SQLite on phase transitions and at session end. Do not write to the DB on every keystroke.
- It holds **two** copies of the editor, per file: `code` (current, updated by every telemetry
  batch) and `previousCode` (the buffer as of the interviewer's last line), both
  `Map<String, String>` keyed by filename — a problem is however many files it needs (an
  HTML/CSS/JS scaffold, or fewer), not one. The pair is what the prompt diffs — see §5.

### Lombok conventions

Applied consistently — do not hand-roll what these generate:

| Annotation | Where | Replaces |
|---|---|---|
| `@RequiredArgsConstructor` | every `@Service` / `@Component` / `@RestController` with injected deps | the constructor; Spring injects the generated one |
| `@Slf4j` | anything that logs | `private static final Logger log = LoggerFactory.getLogger(X.class)` |
| `@Getter` / `@Setter` | JPA entities only | accessor walls |
| `@NoArgsConstructor(access = PROTECTED)` | JPA entities | the `protected X() {}` JPA requires |

Two deliberate exceptions:

- **`GeneratedProblemSource` keeps its explicit constructor** — it logs an error when the API key is
  missing, so there is a body to write. `@RequiredArgsConstructor` cannot express that.
- **`SessionState` uses no Lombok at all.** Its accessors are fluent (`state.code()`, not
  `getCode()`), most fields are `Atomic*` or `volatile` with real logic in the accessor, and the
  concurrency intent is the point of the class. Generated accessors would hide it. Leave it hand-written.

`TelemetryEvent` gets `@Getter` but **no `@Setter`** — it is the append-only replay log, and nothing
should ever update a row.

### Core entities

`Session`, `Problem`, `TelemetryEvent`, `Utterance` (what the AI said + why it fired), `ReportCard`.
`TelemetryEvent` is the replay log — keep it append-only and cheap to write.

---

## 5. OpenAI integration

We're on OpenAI because the hackathon supplied credits. Everything in `ai/` uses the **official
`com.openai:openai-java` SDK** — not raw HTTP, not a community wrapper, not LangChain4j.

Entry points (verified against the 4.65.0 jar):

```java
com.openai.client.OpenAIClient                 // interface
com.openai.client.okhttp.OpenAIOkHttpClient    // .fromEnv() reads OPENAI_API_KEY
com.openai.models.responses.ResponseCreateParams
com.openai.models.chat.completions.ChatCompletionCreateParams
```

Build one `OpenAIClient` as a singleton `@Bean`. It is thread-safe; do not construct one per request.
It is wrapped in `OpenAiClientHolder` so the app starts cleanly with no key set — see §5.1.

### Two Jacksons are on the classpath. This will bite you.

Spring Boot 4 ships **Jackson 3** (`tools.jackson.databind`). The OpenAI SDK pulls in **Jackson 2**
(`com.fasterxml.jackson`) for its own schema generation. Both coexist fine, but:

- **Injecting an `ObjectMapper`?** Import `tools.jackson.databind.ObjectMapper`. Only the Jackson 3
  mapper exists as a bean; asking for the Jackson 2 one fails context startup with a confusing
  "No qualifying bean of type ObjectMapper".
- **Annotating a structured-output record?** Use the Jackson 2 annotations
  (`com.fasterxml.jackson.annotation.*`) — those are what the SDK's schema generator reads. See
  `Reaction`.
- Jackson 3 throws **unchecked** exceptions, so `catch (IOException)` around a `readValue` no longer
  compiles. Catch `RuntimeException`.

**Prefer the Responses API** (`client.responses()`) over Chat Completions for new code — it is OpenAI's
current surface and handles reasoning models and structured output more cleanly.

Model IDs are **configuration, not constants** — `humancode.ai.model` and `humancode.ai.quip-model`.
The defaults are starting guesses; verify them against what the credits actually cover before the first
run and change them in `application.properties`, never in code.

### 5.1 Running without a key

`OpenAiClientHolder` holds a possibly-absent client. With no `OPENAI_API_KEY`, `Interviewer` falls back
to `CannedLines` and logs a warning at startup — triggers still fire, the meter still moves, SSE still
streams, the UI still works. Every utterance carries a `canned` flag so the UI can mark it.

This is not only for the missing-key case: a model call that times out mid-session falls back the same
way. **An interview that goes silent because of a network blip is a broken demo.** Keep that property
when you extend the AI layer — `Interviewer.react` must never throw and never return empty.

### 5.2 Setting the key

`application.properties` binds it, and nothing else needs changing:

```properties
spring.config.import=optional:file:.env[.properties]
humancode.ai.api-key=${OPENAI_API_KEY:}
```

Three ways in, all equivalent to the app:

1. **`.env` at the repo root** (gitignored) — `OPENAI_API_KEY=sk-...`. Works because of the
   `spring.config.import` line above; Spring Boot does **not** read `.env` without it.
2. **Environment variable** — `$env:OPENAI_API_KEY = "sk-..."` before launching (that shell only), or
   `[Environment]::SetEnvironmentVariable('OPENAI_API_KEY', 'sk-...', 'User')` to persist. A persisted
   variable needs a **new terminal**, and a restart of any IDE that was already running.
3. **IDE run configuration** environment variables.

The trailing `:` in `${OPENAI_API_KEY:}` is the empty default — it is what lets the app boot with no key.
**Never** put a literal key in `application.properties`; that file is committed.

Confirm which mode you are in from the startup log: `OpenAI client ready (model=…, quipModel=…)` versus
the `OPENAI_API_KEY is not set` warning. In the UI, the faint `canned` marker beside each interviewer
line disappears once real calls are happening.

### Two call paths

**Quip path** — reactive one-liners, fires on triggers, must feel instant. Cheap model
(`humancode.ai.quip-model`), minimal reasoning effort, small output cap, non-streaming, **structured
output** so the client receives `{ line, mood, impatienceDelta }` instead of prose it has to parse.

**Deliberate path** — problem statement, hints, the report card. Full model (`humancode.ai.model`).
Not streamed yet: every call in the app today, including the report card, is non-streaming;
token-by-token streaming onto the session's SSE channel is still open (§9).

Curveballs are neither of these. They cost no model call at all — see §2.

### Prompt caching

OpenAI caches automatically — there is no `cache_control` to set — but it is still a **prefix match** and
only kicks in above a token floor. The stable-prefix discipline therefore matters exactly as much as it
would with an explicit API:

```
[ stable prefix ] system prompt → problem statement → reference solution → rubric
[ volatile tail ] current code snapshot → diff since the last line → event digest → trigger
```

**Never** interpolate a timestamp, session id, or elapsed-time counter into the prefix — one moving byte
costs you every cached read for the rest of the session. Assemble the prefix once per session in
`PromptAssembler` and treat it as immutable.

Verify it is working: the response usage reports cached prompt tokens
(`prompt_tokens_details.cached_tokens`). If that stays zero across consecutive quips in one session,
something in your prefix is moving — find it before you tune anything else.

Problems are resource files precisely so swapping one swaps a whole cache namespace cleanly.

### The diff is what makes reactions specific

The tail carries **both** buffers' worth of information: the current editor contents, and a line diff
against `SessionState.previousCode` — the code as it stood the last time the interviewer actually
spoke. `ai/CodeDiff.java` renders one file's diff as `-`/`+` lines with line numbers, capped at 40;
`unifiedAcrossFiles` calls it once per file and headers each block with the filename, so a reaction
can point at the file that actually changed rather than a single undifferentiated blob.

Without it the model only ever sees a still frame, so it describes the same shape of code every time
and the session flattens into three interchangeable remarks. With it, a line can be about the thing
that just moved — the map that just got deleted, the loop that replaced it, the ten minutes in which
nothing appeared at all — which is both more varied and more pointed, at no cost to the tone.

Two rules to keep it honest:

- **The baseline moves only when a line is actually spoken.** `InterviewDirector.fire()` calls
  `state.markCodeSpokenFor()` after `interviewer.react`, and after the cooldown and SSE guards. Move
  it earlier and a suppressed trigger silently eats the candidate's work; move it into the telemetry
  path and the diff shrinks to a 1.5s sliver of typing that is never worth a sentence.
- **It belongs in the tail, never the prefix.** It changes on every call by construction, so one byte
  of it near the front would cost every cached read in the session.

### Reasoning models will eat your output budget

`gpt-5` and `gpt-5-mini` are reasoning models: `maxOutputTokens` covers the reasoning tokens *and* the
visible answer. Set it too low and the call still returns 200, with `status=incomplete`,
`incomplete_details.reason=max_output_tokens`, a reasoning item, and **no message**. The SDK's
`response.output()` stream then yields nothing, which is indistinguishable from a model that declined
to answer — so every line quietly comes back canned while the logs report success.

That is exactly what a 160-token cap on the quip path did. The fix is both halves:

```java
.reasoning(Reasoning.builder().effort(ReasoningEffort.MINIMAL).build())
.maxOutputTokens(400L)
```

A heckle is not a reasoning problem and the candidate is waiting, so minimal effort is right on its
own merits — but keep the headroom too, because effort is a hint, not a guarantee. Both call sites log
`status` and `incomplete` on an empty response now; if you see `incomplete=max_output_tokens`, raise
the cap rather than hunting the prompt.

### Structured outputs

Every machine-consumed response — reactions, report cards, generated problems — uses **Structured Outputs
with a strict JSON schema**. Do not parse prose, and do not ask for JSON in the system prompt and hope.
Check the SDK's structured-output helpers for the current builder surface before writing the first one;
the wiring has changed across 4.x releases.

### Prompt files

Problems live in `src/main/resources/problems/*.json`. The interviewer's voice is a single
constant — `PromptAssembler.RULES`. There is no persona system: one voice, defined in one place.

---

## 6. Verification — there is no runner

**Problems are small apps to build (HTML/CSS/JS), not pure functions with test cases, and there is
no code execution anywhere in this app — not in the browser, not on the server.** This was a
deliberate pivot away from an earlier design where a Web Worker ran the candidate's code against
JSON test cases. Verification is now **judgment**: the interviewer reads the candidate's diff
against the problem's `rubric` — the same trust model the report card already used for grading the
whole session, now applied to every reaction, including the moment the candidate submits.

Rationale for dropping the runner rather than teaching it multi-file HTML/CSS/JS: there is no
meaningful way to assert "the button is yellow" by executing code in a sandboxed Web Worker, because
workers have no DOM. Building a second, DOM-capable sandbox (an iframe) to get that back was a real
option but a materially bigger one — a new execution surface, new failure modes, and a UI panel that
cuts against UI-DESIGN.md's single-column, no-panels rule. Reading the diff was already most of what
made reactions specific (see above); extending that same mechanism to verification cost nothing new
to build.

**There is now an iframe, and it is still not a runner.** `web/src/lib/buildPreview.ts` assembles
the candidate's files into one document and `PreviewPane` renders it, so they can see the app they
are building instead of writing HTML blind (UI-DESIGN.md §4.3b). Read against the paragraph above,
two of the three costs were real and one was avoidable: it *is* a new execution surface, and it
does have its own failure modes — but it is not a panel, because it takes over the editor's slot
rather than sitting beside it. What it deliberately does **not** do is assert anything. Nothing
reads the frame, nothing scores it, and no result leaves it; verification is still the interviewer
reading the diff against the rubric. The moment something starts asserting against that DOM, this
section is wrong and the runner is back.

**`POST /api/sessions/{id}/submit`** (was `/run`) replaced the old test-result endpoint. It takes no
body — there is no local result to report — increments `SessionState.submitCount()`, and fires
`TriggerEngine.onSubmit()`, which goes through the ordinary quip path like any other reaction. The
interviewer's judgment **is not shown**: no pass count, no failure, no green check, anywhere on
screen. It exists to move the trigger engine and to give the interviewer something to be smug about;
the candidate finds out how they did by being told. See UI-DESIGN.md §4.7.

A consequence worth knowing: a subtly wrong reference answer or an over-strict rubric item can now
make the interviewer call correct work wrong, with no automated check to catch it before a candidate
hits it live. `web/scripts/check-problems.mjs` catches structural mistakes (a missing file, no gap
between starter and reference content, too few rubric items or curveballs) but cannot verify a
reference answer is actually correct — there is nothing left to execute it against. Read every bank
problem's reference content like you'd review a PR before it ships.

### Problem sources

`humancode.problems.source` selects a `ProblemSource`:

| Value | Behaviour |
|---|---|
| `generated` (**the default**) | The model writes a fresh app-building problem per session — statement, however many files it needs, rubric, curveballs — via structured outputs (`GeneratedProblem`). Requires `OPENAI_API_KEY`. |
| `bank` (**the `dev` profile**) | `resources/problems/*.json`. Repeatable, free, structurally checked by `check:problems`. `-Dspring-boot.run.profiles=dev`. |

The default generates. That means a plain `./mvnw spring-boot:run` costs a model call per session
started, which is deliberate — the product is the generated interview, and a default that quietly
serves the same three problems is how you demo the wrong thing. Use the `dev` profile while working
on anything else.

### Difficulty

The candidate picks **easy, medium or hard** before starting; it rides on `POST /api/sessions` as
`{"difficulty": "hard"}` and anything unrecognised, including null, means "any" — the behaviour from
before the selector existed. `Difficulty.parse` is the one place that decides.

Two things it touches:

- **The generator is told what the word buys**, in minutes and in technique
  (`ProblemGenerator.calibration`). "Write a hard problem" on its own returns an easy problem with an
  intimidating statement. The requested level then **overwrites** whatever the model labelled it: the
  candidate chose this, and a model that writes an easy problem and calls it hard does not also get
  to relabel the session.
- **`pool-size` is per difficulty.** A pool of three easy problems cannot answer a request for a hard
  one, and substituting silently would make the whole choice decorative — `ProblemPool.take` returns
  empty rather than handing over the wrong level, and the source falls back to a bank problem *of
  that difficulty*.

The bank carries at least one of each (`longest-valid-parentheses` is the hard one), so the `dev`
profile serves every level offline. `ProblemBankTest.coversEveryDifficulty` fails if that stops being
true.

`./mvnw test` runs the two `@SpringBootTest` classes under `@ActiveProfiles("dev")`, because a Spring
context publishes `ApplicationReadyEvent` and would otherwise warm the pool — two model calls and a
minute of latency on every build. Do not remove those annotations. Do not add a
`src/test/resources/application.properties` either: it *replaces* the main one rather than merging,
and the context then fails on a null `humancode.ai`.

### Running in prod

```
./mvnw package
OPENAI_API_KEY=sk-... java -jar target/humancode-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

The `prod` profile adds what a served deployment wants on top of the defaults: a deeper pool, INFO
logging, response compression, no error details on the wire. Generated problems are not part of that
difference any more — they are the default everywhere except `dev` and the tests.

**Generation takes 35-45 seconds**, so it cannot happen in front of the begin button. `ProblemPool`
keeps `humancode.problems.pool-size` problems warm: the session takes one (measured: 250ms end to
end) and a replacement is generated in the background. Three consequences worth knowing before a
demo:

- **The pool survives restarts.** It is written to `humancode.problems.cache-file`
  (`./data/problem-pool.json`, gitignored) on every change and read back at startup — so only a
  genuinely first run is cold. Without it, every restart began with a bank problem, which is exactly
  what you hit while working on the app. Look for `Restored 2 problem(s)` rather than `Pool is empty`.
- **A first run, or a deleted cache, is cold for a minute or so.** Sessions started in that window get
  a bank problem and say so in the log (`Pool still filling; starting this session on bank problem`).
  Wait for `Pool now holds N problem(s)` before starting one.
- **A cold pool with a generation already running does not start a second one.** It waits 8s for the
  in-flight one, then takes the bank. Two 40-second calls to serve one candidate is how a pool ends up
  costing more than no pool.
- **Steady-state cost is one generation per session started**, plus the warm-up. Nothing is generated
  speculatively beyond the target, so an idle server is free.

Two failure modes that cost a real session each before they were fixed, both worth remembering
because neither looks like an error:

- **The client-wide 30s timeout does not fail a slow call, it retries it.** A 40-second generation was
  showing up as a 92-second one — three attempts, three times the tokens, one usable answer.
  `humancode.problems.generation-timeout` (180s) is applied per request via `RequestOptions`.
- **A truncated response is reported as a JSON parse error**, not as a truncation, with the partial
  body pasted into the exception message. 8000 output tokens was not enough for a problem with eight
  tests; it is 16000 now. If you see `Problem generation failed` with a JSON parse error, raise
  `MAX_OUTPUT_TOKENS` before suspecting the prompt.

`generated` falls back to the bank — loudly — when the key is missing or the model returns something
malformed. `ProblemGenerator.convert` structurally validates first: a non-blank statement, at least
one file, every file has a name/language/starter/reference, at least one file's reference content
actually differs from its starter (a starter that *is* the answer passes every other check), at
least three rubric items, and at least one curveball.

**A rejection is not free and it is not safe by default.** It costs a 40-90 second call and produces
nothing, and the retry is silent — so an over-strict check reads as "generation is just slow today".
The lesson predates the rewrite: the old starter-code check compared lengths and rejected every easy
problem whose JSDoc'd empty function was longer than its one-line answer. The same trap is live in
the new shape — the instructions explicitly allow a complete HTML shell whose starter and reference
are identical, so the gap check must be "**any** file has work", never "every file has work".
`ProblemGeneratorTest` pins both halves.

There is no way to verify a generated reference answer is actually correct — that risk is inherent
to dropping the runner (§6) and is trusted the same way the interviewer's live judgment already is.

Because a generated problem exists only for the life of its session, `SessionState` holds the whole
`Problem`, not an id. There is nothing to look it up in.

**`cd web && npm run check:problems`** structurally validates every bank problem — see §6. Run it
after touching any problem JSON.

**`npm run check:pool`** does the same for `data/problem-pool.json`, the problems waiting to be served.
Those were written by a model minutes ago and nobody has ever read them, so this is the one that matters
before a demo. It is the only automated defence against the residual risk above; it proves the tests are
self-consistent with their own reference solution, not that the problem is any good.

---

## 7. Cheating detection

Do the free, reliable thing first: Monaco emits paste events. A 400-character insert with no preceding
keystrokes is a far stronger signal than any text classifier, and it costs nothing. Feed paste bursts into
the trigger engine — the interviewer noticing your paste in real time is better comedy than a detection
score anyway.

GPTZero is a stretch-tier add-on for the report card, not a core dependency.

---

## 8. Conventions

- Run: `./mvnw spring-boot:run` (builds the UI too, bank problems). Prod: `--spring.profiles.active=prod`
  (see §6). Test: `./mvnw test`.
  Backend-only loop: add `-Dskip.frontend=true`. Frontend hot reload: `npm run dev` in `web/`.
- `OPENAI_API_KEY` via `.env` or the environment — see §5.2. Never commit a key, never put a literal
  one in `application.properties`.
- After touching any problem JSON, run `cd web && npm run check:problems`.
- **Run the app through Maven**, not by launching `HumancodeApplication` from the IDE. The IDE build
  skips the `web/dist` → `static` copy, so you get Spring's whitelabel error page instead of the UI.
  If port 8080 is already held by an older `spring-boot:run`, new endpoints 404 — restart it.
- Config under the `humancode.*` prefix, bound with `@ConfigurationProperties`.
- Log every model call with its trigger reason, latency, and cache-hit counts. When the interviewer says
  something strange mid-demo you will want to know which trigger fired.
- The interviewer's tone is **accusatory**: a senior engineer demanding an account, not a narrator.
  It asks more than it states — "why are you still not changing anything?" rather than "no new code",
  "what the hell is that line doing in there?" rather than "that line is useless". Second person,
  always. Mild exasperation is in character (hell, damn, seriously); it accuses the work and the
  decision behind it and **never** the person, never their intelligence, and never anything but the
  code and the clock. `PromptAssembler.RULES` is the only place the voice is defined — with one
  exception, `CannedLines`, which has to match it or the fallback reads as a second interviewer.
- Two mechanical limits hold that voice up, and both bite silently. `ReactionGuard` caps a line at
  **14 words** (raised from 12: a question carries more scaffolding than a statement), and it rejects
  solution language via the shared `ai/SolutionLanguage` — named strategies outright (two pointers,
  binary search, and their kin), plus sequencing language paired with an action ("first sort, then
  scan"). Naming a construct that's already visible in the candidate's code (`loop`, a class, a
  filename) is deliberately *not* blocked — the interviewer can see their screen, so pointing at
  what's already there isn't coaching, and blocking it was flattening every reaction into "why did
  you do that" (bare-noun blocking was tried and reverted for exactly this reason). A rejected line
  is not an error, it is a canned line, so a run of `canned` markers in the UI with no warning in the
  log means the model is writing lines the guard will not pass.

---

## 9. Open decisions

Recorded here so they get made deliberately rather than by accident:

- **Model IDs.** `humancode.ai.model=gpt-5` and `quip-model=gpt-5-mini` are accepted by the API — a
  wrong id 404s, gets caught, and **silently degrades to a canned line**, so the app looks fine while
  saying nothing real. Grep the log for `Quip call failed` or `Problem generation failed` the first
  time the key is in, and for `No structured reaction` if the lines are canned without any error.
- Whether `immediate` triggers need a short cooldown floor (see §2).
- Language support at demo time — JS only, or JS + Python? (Pyodide adds ~10MB and a load delay.)
- Whether passive mode needs server-side scheduling or a client timer is enough.
- Whether the report card's session replay ships in v1 or gets cut for time.

### Still unbuilt

Core loop is closed (problem → code → telemetry → trigger → reaction → verdict), the **report card**
is built (§5, `report/`), and **mid-task curveballs** are built (§2, §6). Not yet built: **hints**,
the **follow-up phase**, and **real token-by-token streaming** — every call today, including the
report card, is non-streaming; see §5.
