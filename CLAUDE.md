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
- Mid-task curveballs: "now handle duplicates", "actually make it O(1) space".

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

**`PASTE_BURST`, `TESTS_PASSED` and `TESTS_FAILED` are `immediate` and bypass the cooldown entirely.**
That is deliberate — a delayed reaction to a paste or a test run feels broken, and the interviewer
catching your paste *as it happens* is the joke. It was also harmless while `run` was a stub nobody
clicked. It is not harmless now: every `run` click is an uncooldowned call, so someone hammering the
button while debugging generates one call per press. If cost or rate limits bite, put a short floor
(~4s) on `immediate` triggers in `InterviewDirector.fire()` rather than removing the immediacy.

With `humancode.problems.source=generated` (the `prod` profile) there is **one generation call per
session started**, but it no longer happens in front of the user: `ProblemPool` keeps a couple warm and
replaces what is taken in the background. See §6.

---

## 3. Stack

**Before touching anything visual, read [UI-DESIGN.md](UI-DESIGN.md).** It is the binding spec for
layout, palette, motion and the anti-LeetCode rules. The short version: **you are the model.** A
Claude Code-style log with the roles swapped — `>` lines are the human on the other side prompting
you, `⏺` blocks are your output, your live turn is the editor, `submit` closes a turn, a heckle
arriving mid-keystroke renders as `⎿ Interrupted by user`, and the footer counts your keystrokes like
tokens while their impatience meter fills. One centred column; no rails, split panes or borders.

| Layer | Choice |
|---|---|
| Backend | Spring Boot 4.1.1, Java 25, Maven (`./mvnw`) |
| Persistence | Spring Data JPA + SQLite (`org.xerial:sqlite-jdbc`) |
| AI | OpenAI, official `com.openai:openai-java` SDK |
| Frontend | React 19 + TypeScript + Vite 8 (`.tsx`), `@monaco-editor/react`, Tailwind 4 |
| Client→server | REST, telemetry batched ~1.5s |
| Server→client | SSE (`SseEmitter`), one stream per session |
| Code execution | **In-browser** (see §6) |

### Dependencies

Already wired in `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-validation`,
`spring-boot-starter-data-jpa`, `sqlite-jdbc`, `hibernate-community-dialects`, `com.openai:openai-java`
(pinned via the `openai-java.version` property), Lombok.

Two notes worth keeping:
- SQLite's Hibernate dialect (`org.hibernate.community.dialect.SQLiteDialect`) ships in
  `hibernate-community-dialects`, **not** in the JDBC driver. Its version is managed by the Spring Boot
  BOM, so do not pin it.
- SQLite is single-writer. `spring.datasource.hikari.maximum-pool-size=1` is deliberate — raising it
  buys nothing and earns intermittent `SQLITE_BUSY` under the telemetry write path.

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
- It holds **two** copies of the editor: `code` (current, updated by every telemetry batch) and
  `previousCode` (the buffer as of the interviewer's last line). The pair is what the prompt diffs —
  see §5.

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

**Deliberate path** — problem statement, hints, curveballs, the report card. Full model
(`humancode.ai.model`), streamed token-by-token onto the session's SSE channel so the interviewer appears
to type.

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
spoke. `ai/CodeDiff.java` renders it as `-`/`+` lines with line numbers, capped at 40.

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

## 6. Running user code

**In the browser, not on the server.** `web/src/workers/testRunner.ts` evaluates the candidate's code in
a throwaway Web Worker and runs it against the problem's test cases; `web/src/lib/runTests.ts` owns the
worker lifecycle and POSTs the verdict to `/api/sessions/{id}/run`.

Rationale: zero sandbox infrastructure, zero RCE surface, zero cold-start latency, and it works behind
conference wifi. A server-side runner (Docker, Judge0, Piston) is a project on its own and buys nothing
the demo needs.

Three things about this that are load-bearing:

- **The timeout lives on the main thread, not in the worker.** A worker cannot interrupt its own
  `while (true)`, so `runTests` sets a 3s timer and calls `worker.terminate()`. An infinite loop is a
  *likely* outcome in a timed interview, not an edge case — verified: the page stays fully responsive.
- **Args are `structuredClone`d per case**, so a candidate who mutates the input cannot corrupt the
  next test.
- **`match: "unordered"`** exists for problems like Two Sum where "return the indices in any order"
  means `[1,0]` is as correct as `[0,1]`. Default is `exact`.

**Test cases are visible to the candidate**, unavoidably — the worker runs in their browser and cannot
execute a test it was not given. Inputs and outputs are not the algorithm, and `Problem.forCandidate()`
still strips the reference solution, complexity, rubric and follow-ups. Genuinely hidden tests would
need a server-side runner.

**The verdict is not.** The run result is POSTed to `/api/sessions/{id}/run` and dropped — the UI never
renders a pass count, a failure or a green check. It exists to move the trigger engine and to give the
interviewer something to be smug about; the candidate finds out how they did by being told. See
UI-DESIGN.md §4.7 before adding any readout.

### Problem sources

`humancode.problems.source` selects a `ProblemSource`:

| Value | Behaviour |
|---|---|
| `generated` (**the default**) | The model writes a fresh problem with its own runnable tests per session, via structured outputs (`GeneratedProblem`). Requires `OPENAI_API_KEY`. |
| `bank` (**the `dev` profile**) | `resources/problems/*.json`. Repeatable, machine-verified, free. `-Dspring-boot.run.profiles=dev`. |

The default generates. That means a plain `./mvnw spring-boot:run` costs a model call per session
started, which is deliberate — the product is the generated interview, and a default that quietly
serves the same three problems is how you demo the wrong thing. Use the `dev` profile while working
on anything else.

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
malformed. `ProblemGenerator.convert` structurally validates first: entry point is a real JS identifier,
at least three tests with no duplicate `argsJson`, a statement, reference solution and starter code both
defining the entry point, starter code shorter than the reference (a starter that *is* the answer passes
every other check), and every `argsJson`/`expectedJson` parsing as JSON.

A subtly *wrong* test can still get through, and that risk got sharper now that the UI never shows test
results: a candidate with a correct answer is told they are wrong and has no way to see why. Verifying
generated tests for real needs a server-side JS runtime, which is a project of its own — §6 says why we
do not have one.

Because a generated problem exists only for the life of its session, `SessionState` holds the whole
`Problem`, not an id. There is nothing to look it up in.

**`cd web && npm run check:problems`** runs every bank problem's reference solution against its own test
cases. Run it after touching any problem JSON — a wrong expectation is invisible until a candidate writes
a correct answer and gets called wrong, which is the worst possible place to find it.

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
  any line containing solution language — `loop`, `set`, `sort`, `stack` and friends. A rejected line
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

Core loop is closed (problem → code → telemetry → trigger → reaction → tests → verdict). Not yet built:
the **report card** (`/api/sessions/{id}/finish` closes the session but generates nothing), **hints**,
**mid-task curveballs**, the **follow-up phase**, and the **deliberate streamed call path** — §5 describes
it, but every call today is the quip path.
