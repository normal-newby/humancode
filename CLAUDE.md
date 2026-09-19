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
- Persona selector: Senior Engineer, CS Professor, Football Coach, Startup Founder, Hostile FAANG Screener.

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

---

## 3. Stack

**Before touching anything visual, read [UI-DESIGN.md](UI-DESIGN.md).** It is the binding spec for
layout, palette, motion and the anti-LeetCode rules. The short version: Monkeytype-style restraint,
the AI speaks from a band across the top, private notes on the right, instruments on the left, and no
split panes or bordered cards anywhere.

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
ai/          OpenAI client wrapper, PromptAssembler, personas, structured reaction types
problem/     Problem bank (JSON resources), selection, generation
report/      End-of-session report card
web/         REST controllers, SSE hub, DTOs
```

Rules:
- Controllers are thin. All logic lives in the service layer.
- DTOs are Java `record`s. Entities use Lombok.
- `SessionState` lives in memory (a `ConcurrentHashMap` keyed by session id) and is *snapshotted* to
  SQLite on phase transitions and at session end. Do not write to the DB on every keystroke.

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
[ stable prefix ] system prompt → persona block → problem statement → reference solution → rubric
[ volatile tail ] current code snapshot → recent event digest → trigger that fired
```

**Never** interpolate a timestamp, session id, or elapsed-time counter into the prefix — one moving byte
costs you every cached read for the rest of the session. Assemble the prefix once per session in
`PromptAssembler` and treat it as immutable.

Verify it is working: the response usage reports cached prompt tokens
(`prompt_tokens_details.cached_tokens`). If that stays zero across consecutive quips in one session,
something in your prefix is moving — find it before you tune anything else.

Personas and problems are resource files precisely so swapping one swaps a whole cache namespace cleanly.

### Structured outputs

Every machine-consumed response — reactions, report cards, generated problems — uses **Structured Outputs
with a strict JSON schema**. Do not parse prose, and do not ask for JSON in the system prompt and hope.
Check the SDK's structured-output helpers for the current builder surface before writing the first one;
the wiring has changed across 4.x releases.

### Prompt files

Personas live in `src/main/resources/personas/*.md`, problems in `src/main/resources/problems/*.json`.
Editing tone should never require a recompile.

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

### Problem sources

`humancode.problems.source` selects a `ProblemSource`:

| Value | Behaviour |
|---|---|
| `bank` (default, **use in dev**) | `resources/problems/*.json`. Repeatable, and the expectations are machine-verified. |
| `generated` (**prod**) | The model writes a fresh problem with runnable tests per session, via structured outputs (`GeneratedProblem`). Requires `OPENAI_API_KEY`. |

`generated` falls back to the bank — loudly — when the key is missing or the model returns something
malformed. `ProblemGenerator.convert` structurally validates first: entry point present, tests non-empty,
reference solution and starter code both defining that function, every `argsJson`/`expectedJson` parsing
as JSON. A subtly *wrong* test can still get through; that is the residual risk of generating problems.

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

- Run: `./mvnw spring-boot:run` (builds the UI too). Test: `./mvnw test`.
  Backend-only loop: add `-Dskip.frontend=true`. Frontend hot reload: `npm run dev` in `web/`.
- `OPENAI_API_KEY` from the environment — `OpenAIOkHttpClient.fromEnv()`. Never commit a key,
  never put a literal one in `application.properties` (`humancode.ai.api-key` reads the env var and is
  only there as an override).
- Config under the `humancode.*` prefix, bound with `@ConfigurationProperties`.
- Log every model call with its trigger reason, latency, and cache-hit counts. When the interviewer says
  something strange mid-demo you will want to know which trigger fired.
- The interviewer's tone is smug, impatient, and funny. It is **never** genuinely cruel, and it never
  comments on anything but the code and the clock. Persona prompts carry this constraint explicitly.

---

## 9. Open decisions

Recorded here so they get made deliberately rather than by accident:

- Language support at demo time — JS only, or JS + Python? (Pyodide adds ~10MB and a load delay.)
- Whether passive mode needs server-side scheduling or a client timer is enough.
- Whether the report card's session replay ships in v1 or gets cut for time.
