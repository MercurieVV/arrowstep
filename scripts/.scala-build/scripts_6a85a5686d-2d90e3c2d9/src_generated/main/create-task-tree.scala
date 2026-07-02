

final class create$minustask$minustree$_ {
def args = create$minustask$minustree_sc.args$
def scriptPath = """scripts/create-task-tree.sc"""
/*<script>*/
//> using scala 3.8.4
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep com.lihaoyi::ujson:4.4.3

// Create the arrowstep implementation task tree on GitHub as native sub-issues.
//   scala-cli run scripts/create-task-tree.sc
// Idempotent labels; issues/links are created fresh each run (rerunning duplicates issues).
// Requires: `gh` authenticated with `repo` scope.

val Owner = "MercurieVV"
val Repo  = "arrowstep"
val Api   = s"repos/$Owner/$Repo"

final case class Node(
    key: String,
    title: String,
    parent: String,          // "" for root
    deps: List[String],
    engineLabel: String,     // "" for epics
    modelLabel: String,
    phase: String,           // "all" | "0".."6"
    depth: Int,
    nodeType: String,        // "epic" | "leaf"
    body: String
)

def epic(key: String, title: String, phase: String, body: String, parent: String = "root") =
  Node(key, title, parent, Nil, "", "", phase, if key == "root" then 0 else 1, "epic", body)

def leaf(
    key: String, title: String, parent: String, deps: List[String],
    engineLabel: String, modelLabel: String, phase: String,
    engineHuman: String, modelHuman: String, routing: String, body: String
) =
  val header =
    s"""**Phase:** $phase · **Depth:** 2 (leaf) · **Parent:** #${parent.toUpperCase}
       |**Assigned worker:** `$engineHuman` — `$modelHuman`  (routing: $routing)
       |_Depends-on / Blocks issue numbers appended below; Children shown natively (none — leaf)._
       |
       |""".stripMargin
  Node(key, title, parent, deps, engineLabel, modelLabel, phase, 2, "leaf", header + body)

// ============================ TREE ============================
val root = epic("root", "Implement arrowstep (Phases 0–6)", "all",
"""## Epic: build the arrowstep library end-to-end

arrowstep = a Scala 3 library for **typed, compiler-checked, replayable dialogues between programs and coding agents**. One dialogue = one lawful `cats` arrow composition. Leadership (agent vs program), which LLM answers, and transport are runtime plug-ins behind typed boundaries.

This epic tracks the full [ROADMAP](../blob/master/docs/ROADMAP.md) (Phases 0–6). Read [DESIGN.md](../blob/master/docs/DESIGN.md) and [DECISIONS.md](../blob/master/docs/DECISIONS.md) first.

**Depth:** 0 (root) · **Children:** the 7 phase epics (native sub-issues below).

Architecture layers: **L0** wire protocol (bash contract) -> **L1** core (pure, tagless-final cats Arrows) -> **L2** runtime (cats-effect edge) -> **L3** consumer flow. `ValidAnswers` is opaque, constructible only by a `Validator`, so unvalidated agent output cannot reach the effects stage (compile error, not convention).

Every phase ends with something runnable. Each leaf carries an assigned worker engine+model (labels `engine:*`/`model:*`) per `.claude/orchestrate-routing.md`; claude reserved for the hardest Scala.""",
  parent = "")

val phaseEpics = List(
  epic("p0", "Phase 0 — Skeleton", "0",
"""## Phase 0 — Skeleton

Goal: a runnable end-to-end skeleton. Core arrow + vocabulary + validator, a file-backed replay runtime, stubs, and an example flow executed by an MUnit test with `StubAsk`.

**Depth:** 1 · **Parent:** #ROOT · **Children:** 10 leaves (native sub-issues).

Exit criterion: `mill app.test` runs the example dialogue flow purely (no disk, no agent) and asserts on the produced result. Stack (D10): Scala 3, scala-cli + Mill, `cats-core`, `cats-effect`, `os-lib`, `ujson`. Package split (D10): `core` (no effects-runtime coupling), `runtime`, `example`."""),
  epic("p1", "Phase 1 — Protocol hardening", "1",
"""## Phase 1 — Protocol hardening

Goal: make the L0 wire protocol robust and give consumers a 5-line `main`.

**Depth:** 1 · **Parent:** #ROOT · **Children:** 4 leaves.

Covers: centralized/derived JSON codecs with golden-file tests (no schema drift), `AgentMain` entry helper (flag parsing + `ProgramSays`->exit-code + stdout/stderr discipline, D1/D9), inline `--answers` persisted to the log (D2), and the full `Rejected` re-ask loop (D7)."""),
  epic("p2", "Phase 2 — Replay completeness", "2",
"""## Phase 2 — Replay completeness

Goal: make replay determinism (D3) ergonomic and testable.

**Depth:** 1 · **Parent:** #ROOT · **Children:** 3 leaves.

Covers: a `Cached` step helper (run-once nondeterministic effects stored in the answer log), a replay-determinism test-kit (run a flow twice against one log, assert identical question sequences), and answer-log hygiene (stale-answer handling, `--reset`)."""),
  epic("p3", "Phase 3 — Program-led mode (LiveAsk)", "3",
"""## Phase 3 — Program-led mode (LiveAsk)

Goal: the program can summon and own agent sessions (D4, D5, D6).

**Depth:** 1 · **Parent:** #ROOT · **Children:** 4 leaves.

Covers: `AdapterRegistry` (load `.agents/adapters.json`, ship claude/gemini presets), `LiveAsk` (template expansion, process spawn, stream to stderr + log file, final-JSON parse, own-session persistence), session lifecycle flags (`--fresh`, `--resume-session`), and explicit context briefs (`AskInput.context`)."""),
  epic("p4", "Phase 4 — Parallelism & observability", "4",
"""## Phase 4 — Parallelism & observability

Goal: multiple agents concurrently, human-followable (D8, D9).

**Depth:** 1 · **Parent:** #ROOT · **Children:** 4 leaves.

Covers: a `parAsk` combinator (concurrent asks over independent question groups / multiple adapters), prefixed interleaved streaming (`[claude#2]`, colored per agent), optional `--panes` tmux integration, and log-file layout + rotation under `.agents/logs/`."""),
  epic("p5", "Phase 5 — First real consumer", "5",
"""## Phase 5 — First real consumer

Goal: prove the library on a real program (G1).

**Depth:** 1 · **Parent:** #ROOT · **Children:** 3 leaves.

Covers: migrating `scala-llm-template/Setup.scala` onto arrowstep (replace its `readLine` `getAgentAnswers` protocol with `ReplayAsk`; express Setup as a `Flow`), adding the one-paragraph agent instruction to that project's CLAUDE.md, and filing feedback issues here for whatever the library lacked."""),
  epic("p6", "Phase 6 — Release", "6",
"""## Phase 6 — Release

Goal: publish and document.

**Depth:** 1 · **Parent:** #ROOT · **Children:** 3 leaves.

Covers: publish to Maven Central (`scala-cli publish` or migrate to sbt if cross-building demands), README quickstart with mdoc-checked docs, and a version/compat policy for the wire protocol (L0 is a spec other languages could implement).""")
)

val leaves = List(
  // ---------- Phase 0 ----------
  leaf("p0_scaffold", "Project scaffold: scala-cli + Mill, deps, package split", "p0", Nil,
    "engine:codex", "model:o3", "0", "codex", "o3", "build/config work, no deep Scala",
"""### Goal
Create the build so `mill app.compile` and `scala-cli compile .` both work, with SemanticDB on.

### Details
- Two entry points (D10): **Mill** `build.sc` (modules `app`, `docs`) and **scala-cli** `project.scala`. Keep dependency/plugin lists in sync.
- Scala **3.8.4**. Deps: `org.typelevel::cats-core`, `org.typelevel::cats-effect`, `com.lihaoyi::os-lib`, `com.lihaoyi::ujson`. Tests: MUnit (+ shapeless3 deriving).
- Compiler options: `-Ysemanticdb` **on** in both build.sc and project.scala (ScalaSemantic MCP depends on it). Wartremover (Unsafe warts) + stainless plugins wired but non-blocking to start.
- Package split (D10): `core` (no effects-runtime coupling), `runtime`, `example` — create empty package objects/dirs so later leaves drop in.
- Add `mill prePush` = compile + test.

### Acceptance
- `mill app.compile` succeeds and writes SemanticDB under `out/`.
- `scala-cli compile .` succeeds.
- Empty `example` package compiles.

### References
DECISIONS.md D10; PROJECT.md "Stack & layout"."""),

  leaf("p0_flow", "core.Flow — opaque Kleisli arrow + ArrowChoice", "p0", List("p0_scaffold"),
    "engine:claude", "model:opus", "0", "claude", "opus", "hardest Scala: variance, type/implicit, cats Arrow laws",
"""### Goal
Define the single arrow abstraction the whole library composes with.

### Details / signatures
```scala
opaque type Flow[F[_], -A, B] = cats.data.Kleisli[F, A, B]
```
- Provide combinators: `>>>` (andThen), `&&&` (fanout), `***` (parallel pairs), `first`/`second`, `Flow.id`, `Flow.lift(f: A => B)` (pure — no effect possible, this is the compile-time purity fence, DESIGN §6), `Flow.apply(f: A => F[B])` (effectful).
- Expose/inherit the lawful `ArrowChoice[Flow[F,*,*]]` instance from Kleisli (`+++`, `|||`, `left`/`right`) — do **not** hand-roll the laws; derive from Kleisli so cats' laws hold.
- Mind variance: `-A` contravariant, `B` covariant; ensure the opaque encoding keeps combinator signatures usable by consumers (DESIGN §3, §7 example must typecheck later).
- No `IO` here; `F[_]` abstract. Respect wartremover Unsafe (no null/var/throw/asInstanceOf).

### Acceptance
- `Flow.id >>> f === f` and associativity hold in a cats-laws / MUnit check (or at least a smoke test).
- The DESIGN §7 wiring shape (`inspect >>> (Flow.id &&& (q >>> ask)) >>> makePlan >>> applyPlan`) typechecks against stub signatures.

### References
DESIGN.md §3 "Flow"; DECISIONS.md D8; scala-rules §4 (abstraction-first, tagless)."""),

  leaf("p0_vocab", "core vocabulary — Question/Kinds/Answers/ValidAnswers/Problem/ProgramSays/AskInput", "p0", List("p0_scaffold"),
    "engine:claude", "model:sonnet", "0", "claude", "sonnet", "opaque types + ADT modelling, scala-semantic useful",
"""### Goal
Define the dialogue vocabulary — the L0 message set encoded as L1 types.

### Details / signatures
```scala
final case class Question(id: String, text: String, kind: QuestionKind,
                          default: Option[String], current: Option[String], context: Option[String])
enum QuestionKind { case FreeText; case Choice(allowed: List[String]) }
final case class Problem(questionId: String, message: String)

opaque type Answers      = Map[String, String]   // raw agent output
opaque type ValidAnswers = Map[String, String]   // constructible ONLY by a Validator (D7) — see p0_ask

final case class AskInput(questions: List[Question], context: Option[String])

enum ProgramSays[+R]:
  case NeedInput(context: Option[String], questions: List[Question])
  case Rejected(problems: List[Problem], questions: List[Question])
  case Done(result: R)
```
- `Answers` opaque with a constructor + accessor; `ValidAnswers` opaque with **no public constructor** here (its only factory lives in `Dialogue`/`Validator`, p0_ask) — expose read accessors only.
- `ProgramSays` must map to exit codes (`Done`->0, `NeedInput`/`Rejected`->2) — put the mapping here or note it for `AgentMain` (P1). Exhaustive matching is the point (DESIGN §3).
- No JSON here (codecs are runtime, P1) — keep `core` free of ujson.

### Acceptance
- Compiles in `core`; `ValidAnswers` cannot be constructed from outside its module.
- A test pattern-matches all `ProgramSays` cases (exhaustiveness).

### References
DESIGN.md §2, §3; DECISIONS.md D7."""),

  leaf("p0_ask", "core.Ask + Validator + Dialogue.askUntilValid", "p0", List("p0_flow", "p0_vocab"),
    "engine:claude", "model:opus", "0", "claude", "opus", "hard: ValidAnswers-sole-factory + ArrowChoice re-ask loop",
"""### Goal
Define the typed agent gap and the only door to `ValidAnswers`.

### Details / signatures
```scala
trait Ask[F[_]]:
  def apply(input: AskInput): F[Answers]            // the typed hole where the LLM sits (D7)

trait Validator[F[_]]:
  def validate(qs: List[Question], a: Answers): F[Either[List[Problem], ValidAnswers]]
  // the ONLY factory of ValidAnswers — a basic choice + free-text validator ships here

object Dialogue:
  def askUntilValid[F[_]: Monad](ask: Ask[F], validator: Validator[F]): Flow[F, AskInput, ValidAnswers]
```
- `askUntilValid` = `ask >>> validate`, **looping on rejection**: on `Left(problems)` re-ask with the offending questions + problems attached (build a new `AskInput`), until `Right(valid)`. Express the loop with `Monad`/`ArrowChoice` — no `var`, no manual recursion that breaks purity (scala-rules §2).
- Basic validator: `Choice` answers must be in `allowed`; `FreeText` non-empty (configurable). Precise `Problem` messages (e.g. `'maybe' not in [yes, no]`).
- `ValidAnswers` constructed **only** inside `Validator` — enforces D7 at the type level.

### Acceptance
- Given a validator + a `StubAsk` returning first-bad-then-good answers, `askUntilValid` re-asks once and yields `ValidAnswers`.
- No way to obtain `ValidAnswers` without going through a `Validator` (compile check).

### References
DESIGN.md §3 "askUntilValid"; DECISIONS.md D7; depends on core.Flow and vocabulary."""),

  leaf("p0_answerlog", "runtime.AnswerLog — file-backed answer log (.agents/answers.json)", "p0", List("p0_scaffold", "p0_vocab"),
    "engine:codex", "model:o3", "0", "codex", "o3", "os-lib/ujson IO, isolated",
"""### Goal
Read/write the answer log that also serves as replay memory (D3).

### Details
- File: `.agents/answers.json` — a JSON object mapping question `id` -> answer string.
- API (in `runtime`, cats-effect): `read[F[_]: Sync]: F[Answers]`, `write[F[_]: Sync](a: Answers): F[Unit]`, `merge`/`upsert` helpers. Use `os-lib` for paths, `ujson` for parse/render.
- Create `.agents/` if missing; empty/absent file -> empty `Answers` (not an error).
- Protocol discipline: this writes a data file, not stdout. No human logging to stdout (D9).
- Build `Answers` via the opaque constructor from p0_vocab.

### Acceptance
- Round-trip: `write` then `read` yields the same map; malformed/absent file -> empty.
- MUnit test writes to a temp dir (`os.temp.dir`), asserts file contents.

### References
DESIGN.md §2 (files table), §4 ReplayAsk; DECISIONS.md D2, D3."""),

  leaf("p0_protojson", "runtime.ProtocolJson — render ProgramSays to wire JSON", "p0", List("p0_vocab"),
    "engine:codex", "model:o3", "0", "codex", "o3", "ujson render of an ADT, mechanical",
"""### Goal
Serialize `ProgramSays` to the exact L0 stdout JSON shapes.

### Details / exact shapes (DESIGN §2)
```jsonc
// NeedInput  -> {"status":"need-input","context":..,"questions":[{id,text,kind,allowed?,default,current,context}]}
// Rejected   -> {"status":"rejected","problems":[{questionId,message}],"questions":[...]}
// Done       -> {"status":"done","result":<consumer JSON>}
```
- `kind` renders as `"free-text"` | `"choice"`; `Choice.allowed` becomes the `allowed` array; `FreeText` omits `allowed`.
- Use `ujson`. Field names must match the spec verbatim (agents/other languages parse this). Golden-file tests come in P1 — keep the renderer pure and total so they can pin it.
- `result` is consumer-defined JSON passed through.

### Acceptance
- Each `ProgramSays` case renders to the documented shape; a sample matches byte-for-byte (modulo key order).

### References
DESIGN.md §2 messages; DECISIONS.md D1, D9."""),

  leaf("p0_stubask", "runtime.StubAsk — canned answers for tests", "p0", List("p0_ask"),
    "engine:codex", "model:o4-mini", "0", "codex", "o4-mini", "trivial test double",
"""### Goal
An `Ask[F]` that returns canned `Answers` — no agent, no IO side effects.

### Details
- `StubAsk[F[_]: Applicative](canned: Answers): Ask[F]` (or a small sequence of responses to drive the re-ask loop in p0_ask tests).
- Combined with a no-op effects stage this runs an entire flow as a pure value (G5 "runnable spec").

### Acceptance
- `StubAsk(canned).apply(input)` returns `canned` in `F`.
- Usable to drive `askUntilValid` bad-then-good in the p0_ask test.

### References
DESIGN.md §4 StubAsk; DECISIONS.md D4."""),

  leaf("p0_replayask", "runtime.ReplayAsk — agent leads; replay or NeedInput+exit 2", "p0", List("p0_answerlog", "p0_ask", "p0_protojson"),
    "engine:agy", "model:gemini-3.1-pro-high", "0", "agy", "Gemini 3.1 Pro (High)", "medium Scala, scala-semantic useful not critical",
"""### Goal
The agent-leads runtime: replay answered questions instantly; on the first missing answer, emit `NeedInput` on stdout and exit 2.

### Details (DESIGN §4)
```
apply(input):
  log <- AnswerLog.read
  if every question id in input is present in log -> return those Answers   (replay, fast)
  else -> emit ProgramSays.NeedInput (via ProtocolJson) on STDOUT, then signal exit(2)
```
- Program re-runs from scratch each invocation (D3); steps before the first gap replay in microseconds.
- Exit-2 signalling: `ReplayAsk` should not call `sys.exit` deep in core-adjacent code — surface a `NeedInput` outcome the entry point maps to exit 2 (coordinate with `AgentMain`, P1). For Phase 0 a minimal mechanism is fine but keep stdout = JSON only, human text = stderr (D9).
- Partial answers: if some but not all present, still emit the missing batch.

### Acceptance
- Fully-answered log -> returns `Answers`, emits nothing.
- Missing answer -> emits one `need-input` JSON on stdout and requests exit 2.
- MUnit test with a temp `.agents/answers.json`.

### References
DESIGN.md §4 ReplayAsk; DECISIONS.md D1, D3."""),

  leaf("p0_session", "runtime.SessionStore + AgentAdapter/registry types + LiveAsk stub", "p0", List("p0_vocab"),
    "engine:agy", "model:gemini-3.5-flash-high", "0", "agy", "Gemini 3.5 Flash (High)", "boilerplate/types, large-context",
"""### Goal
Scaffold the program-led plumbing types so Phase 3 drops in.

### Details
- `SessionStore` over `.agents/sessions.json` (`purpose -> session-id`): `get`, `put` (`Sync`, os-lib/ujson). Programs own their sessions, never the parent's (D5).
- `AgentAdapter` type + `AdapterRegistry` type shells modelling `.agents/adapters.json` templates: `new`/`resume` command arrays with `{prompt}`/`{session}` placeholders (D6). No real spawning yet.
- `LiveAsk` **stub**: an `Ask[F]` skeleton with the intended signature (adapter + session + spawn) that compiles but is clearly unimplemented — real impl is Phase 3.

### Acceptance
- All types compile in `runtime`; `SessionStore` round-trips a temp file.
- `LiveAsk` stub compiles and is clearly marked unimplemented.

### References
DESIGN.md §4 LiveAsk, §2 files; DECISIONS.md D5, D6."""),

  leaf("p0_example", "Example consumer flow + MUnit test with StubAsk", "p0", List("p0_ask", "p0_stubask", "p0_flow"),
    "engine:claude", "model:sonnet", "0", "claude", "sonnet", "arrow wiring across the API; scala-semantic useful",
"""### Goal
Demonstrate and pin the birdview: one arrow pipeline value = the whole dialogue, executed purely in a test.

### Details (DESIGN §7)
- In package `example`, build a small flow, e.g.:
```scala
val inspect: Flow[F, In, Detected]
val questionsFor: Flow[F, Detected, AskInput]                 // Flow.lift (pure)
val askValidated: Flow[F, AskInput, ValidAnswers]            // Dialogue.askUntilValid(ask, validator)
val makePlan: Flow[F, (Detected, ValidAnswers), Plan]        // Flow.lift (pure, deterministic by type)
val applyPlan: Flow[F, Plan, Report]                         // the only world-writing step
def flow(ask: Ask[F]) = inspect >>> (Flow.id &&& (questionsFor >>> askValidated)) >>> makePlan >>> applyPlan
```
- MUnit test runs `flow(StubAsk(canned))` with a no-op/effect-free apply stage and asserts on the produced `Report` — **no disk, no agent** (G5).
- This is the Phase 0 exit criterion.

### Acceptance
- `mill app.test` runs the example flow purely and asserts the result.
- Swapping `StubAsk` for another `Ask` requires changing nothing else in the flow (G6).

### References
DESIGN.md §7; DECISIONS.md D8, G2/G5/G6."""),

  // ---------- Phase 1 ----------
  leaf("p1_codecs", "Centralized/derived JSON codecs + golden-file tests", "p1", List("p0_protojson", "p0_vocab"),
    "engine:agy", "model:gemini-3.1-pro-high", "1", "agy", "Gemini 3.1 Pro (High)", "codec derivation, medium",
"""### Goal
One central place deriving JSON codecs for every L0 message; golden-file tests lock the wire shapes so schema cannot drift (G4).

### Details
- Centralize/derive codecs for `Question`, `QuestionKind`, `Problem`, `AskInput`, and all `ProgramSays` cases. Keep `core` JSON-free — codecs live in `runtime`.
- Golden files: store canonical JSON samples under test resources; assert render == golden and (where sensible) parse round-trips.
- Field names/kinds must match DESIGN §2 exactly (`need-input`, `rejected`, `done`, `free-text`, `choice`).

### Acceptance
- Changing a message field without updating the golden fails the test.
- Round-trip parse/render stable.

### References
DESIGN.md §2; DECISIONS.md D9; builds on ProtocolJson."""),

  leaf("p1_agentmain", "AgentMain entry helper — flags, Ask selection, exit codes", "p1", List("p0_replayask", "p0_protojson", "p0_example"),
    "engine:claude", "model:sonnet", "1", "claude", "sonnet", "medium Scala, stdout/stderr discipline, mode selection",
"""### Goal
`AgentMain.run(flow, mode)` so a consumer's `main` is ~5 lines (DESIGN §4).

### Details
- Parse flags: `--agent` (ReplayAsk), `--autonomous`/program-led (LiveAsk, wired in P3), `--answers`, `--fresh`, `--resume-session <id>`, `--adapter <name>`.
- Select the `Ask` implementation from the mode; run the flow; render final `ProgramSays` via ProtocolJson to **stdout only**; map `Done`->exit 0, `NeedInput`/`Rejected`->exit 2 (D1). All human/diagnostic text to **stderr** (D9).
- Integrate the `ReplayAsk` exit-2 signalling cleanly (the mechanism p0_replayask deferred to the entry point).

### Acceptance
- A consumer `main` using `AgentMain.run` exits 0 on done, 2 on need-input, emits exactly one JSON object on stdout.
- Flag parsing unit-tested.

### References
DESIGN.md §4 entry-point; DECISIONS.md D1, D2, D9."""),

  leaf("p1_inlineanswers", "Inline --answers accepted and persisted to the log", "p1", List("p0_answerlog"),
    "engine:codex", "model:o3", "1", "codex", "o3", "small, isolated",
"""### Goal
Accept `--answers '<json>'` as a convenience and immediately persist it into `.agents/answers.json` (D2).

### Details
- Parse the inline JSON (object id->string), merge into the answer log via `AnswerLog`, then proceed as if it had been read from file. Inline is a convenience; the file is the source of truth and the post-mortem record (D2).
- Guard against malformed inline JSON with a clear error to stderr (no crash on stdout).

### Acceptance
- Running with `--answers` writes the merged log to disk and the flow sees the answers.
- Malformed `--answers` -> clear stderr error, documented non-2 exit.

### References
DECISIONS.md D2; depends on AnswerLog."""),

  leaf("p1_rejected", "Rejected loop end-to-end (validator problems re-emitted)", "p1", List("p0_ask", "p0_replayask"),
    "engine:agy", "model:gemini-3.1-pro-high", "1", "agy", "Gemini 3.1 Pro (High)", "medium Scala, ties core loop to wire",
"""### Goal
Wire the `Rejected` protocol state through the runtime: when the answer log contains answers that fail validation, re-emit the offending questions with `problems` (exit 2), not a generic need-input.

### Details
- In the ReplayAsk path: read answers, run the `Validator`; on `Left(problems)` emit `{"status":"rejected", problems, questions}` (the offending questions restated) and exit 2; the agent fixes `.agents/answers.json` and reruns.
- Reuses `askUntilValid` semantics (p0_ask) but surfaces the rejection to L0 instead of looping in-process when the agent leads.

### Acceptance
- Log with an invalid choice -> one `rejected` JSON on stdout, exit 2, listing the precise `Problem`.
- After the agent corrects the answer, rerun -> proceeds.

### References
DESIGN.md §2 (rejected shape), §3; DECISIONS.md D7."""),

  // ---------- Phase 2 ----------
  leaf("p2_cached", "Cached step helper — run-once effects stored in the answer log", "p2", List("p0_answerlog", "p0_flow"),
    "engine:claude", "model:sonnet", "2", "claude", "sonnet", "determinism design, medium-hard Scala",
"""### Goal
Make D3 requirement (c) ergonomic: nondeterministic reads (e.g. latest Scala version from Maven) run **once**, their result cached into the answer log, so replays see a constant.

### Details
- A `Cached` combinator/`Flow` helper: `cached(key)(effect: F[String]): Flow[F, Unit, String]` returning the stored value if `key` is in the log, else running `effect`, storing it, returning it.
- Storage reuses `AnswerLog` (or a parallel namespaced section) so replay determinism holds: same log => same values => same question sequence.
- Document the determinism contract on consumers (DESIGN §4, §6).

### Acceptance
- First run executes the effect and writes the value; replay reads it without executing the effect (assert via a counter).

### References
DECISIONS.md D3 (req c); DESIGN.md §4, §6."""),

  leaf("p2_determkit", "Replay determinism test-kit", "p2", List("p0_replayask", "p0_example"),
    "engine:codex", "model:o3", "2", "codex", "o3", "test-kit authoring",
"""### Goal
A reusable MUnit helper: run a flow twice against the same answer log and assert identical question sequences (the D3 determinism contract).

### Details
- Helper captures the sequence of `NeedInput` question batches emitted across runs given a fixed log, and asserts run-1 == run-2.
- Provide it as a small testing utility consumers can call on their own flows.

### Acceptance
- Passes for the Phase 0 example flow; fails (demonstrably) for an intentionally nondeterministic flow.

### References
DECISIONS.md D3 (req a); DESIGN.md §4."""),

  leaf("p2_hygiene", "Answer-log hygiene — stale answers + --reset", "p2", List("p0_answerlog"),
    "engine:codex", "model:o3", "2", "codex", "o3", "isolated file logic + flag",
"""### Goal
Handle answers for questions that no longer exist and provide a `--reset`.

### Details
- Detect/ignore stale entries (ids not asked in the current flow); optionally prune on write.
- `--reset` clears `.agents/answers.json` (fresh start). Coordinate the flag with `AgentMain`.
- Do not delete user data silently without the flag; log actions to stderr.

### Acceptance
- Stale ids don't break replay; `--reset` empties the log; behavior unit-tested on a temp dir.

### References
DECISIONS.md D3; DESIGN.md §2."""),

  // ---------- Phase 3 ----------
  leaf("p3_adapterreg", "AdapterRegistry — load adapters.json + ship presets", "p3", List("p0_session"),
    "engine:agy", "model:gemini-3.5-flash-high", "3", "agy", "Gemini 3.5 Flash (High)", "config loading + presets, boilerplate",
"""### Goal
Load `.agents/adapters.json` and ship claude + gemini presets (D6).

### Details
```json
{"claude":{"new":["claude","-p","--output-format","json","{prompt}"],
           "resume":["claude","-p","--resume","{session}","--output-format","json","{prompt}"]},
 "gemini":{"new":["gemini","..."],"resume":["gemini","..."]}}
```
- `resolve(name | default)` returns an `AgentAdapter` with `new`/`resume` templates. Ship presets baked in; user file overrides/extends. Switching agents on the fly = choosing a different adapter for the next `ask` (G7).

### Acceptance
- Missing file -> presets used; present file -> merged/overridden; unknown adapter -> clear error.

### References
DECISIONS.md D6, G7; builds on the registry types from p0_session."""),

  leaf("p3_liveask", "LiveAsk implementation — spawn, stream, parse, own session", "p3", List("p3_adapterreg", "p0_session"),
    "engine:claude", "model:sonnet", "3", "claude", "sonnet", "multi-concern cats-effect: process, streaming, parsing",
"""### Goal
Implement program-led `Ask`: summon an agent CLI, stream its output, parse its final JSON reply (D4, D5, D6).

### Details (DESIGN §4)
```
apply(input):
  adapter <- AdapterRegistry.resolve(requested | default)
  session <- SessionStore.get(purpose)                 // .agents/sessions.json
  cmd     <- session.fold(adapter.new)(adapter.resume) // expand {prompt}/{session}
  spawn cmd; stream stdout+stderr -> parent STDERR + .agents/logs/<purpose>.log
  parse final JSON -> Answers; persist session id (own session, never parent's — D5)
```
- Template expansion of `{prompt}` (the rendered `AskInput` incl. explicit `context` brief) and `{session}`.
- Streaming to stderr + per-purpose log file (D9); protocol JSON never mixed into stdout.
- Robust final-JSON extraction from the CLI output (e.g. `--output-format json`).
- Use cats-effect process APIs (os-lib/`ProcessBuilder`); no blocking on the main thread; no `unsafeRunSync`.

### Acceptance
- With a fake adapter (echo script) it spawns, streams to a log, parses answers, and persists a session id; resume path reuses it.

### References
DESIGN.md §4 LiveAsk; DECISIONS.md D4, D5, D6, D9."""),

  leaf("p3_sesslifecycle", "Session lifecycle — --fresh / --resume-session, purpose-scoped ids", "p3", List("p3_liveask"),
    "engine:codex", "model:o3", "3", "codex", "o3", "flag + store logic",
"""### Goal
Caller controls session continuity (D5 corollary).

### Details
- `--fresh` -> ignore stored session, start new, overwrite. `--resume-session <id>` -> use the given id. Default -> use stored `purpose -> id` or create.
- Purpose-scoped ids in `.agents/sessions.json`; nesting stays uniform (each layer owns its sessions, passes ids down explicitly).

### Acceptance
- Each flag path exercised against a temp `sessions.json`; correct id chosen and persisted.

### References
DECISIONS.md D5; DESIGN.md §4."""),

  leaf("p3_context", "Context briefs — AskInput.context rendered into fresh-session prompts", "p3", List("p3_liveask", "p0_vocab"),
    "engine:agy", "model:gemini-3.5-flash-high", "3", "agy", "Gemini 3.5 Flash (High)", "prompt templating, small-medium",
"""### Goal
A fresh sub-session knows nothing of the parent; pass context **explicitly as typed data** (D5 corollary).

### Details
- Render `AskInput.context` (and per-`Question.context`) into the prompt LiveAsk sends for **fresh** sessions; on resume it may be omitted. The context contract is visible typed data, not ambient conversation.

### Acceptance
- Fresh spawn prompt includes the context brief; resume path can skip it. Verified with a fake adapter capturing the prompt.

### References
DECISIONS.md D5 corollary; DESIGN.md §2, §4."""),

  // ---------- Phase 4 ----------
  leaf("p4_parask", "parAsk combinator — concurrent asks over independent question groups", "p4", List("p3_liveask", "p0_ask"),
    "engine:claude", "model:opus", "4", "claude", "opus", "hardest: cats-effect concurrency + arrow semantics",
"""### Goal
Consult different agents concurrently for independent question groups (G8) without changing the arrow layer.

### Details (DESIGN §5)
- `parMapN`/`parTraverse` over `ask` calls at L2; the arrow layer is unchanged (`&&&` of flows whose runtime happens to parallelize).
- Must preserve determinism/replay semantics and validation. Handle partial failure (one agent fails) with a clear, non-corrupting outcome.
- Requires `Concurrent`/`Parallel` constraints on `F`.

### Acceptance
- Two independent question groups run concurrently against two fake adapters; results merged correctly; a failing branch surfaces cleanly.

### References
DESIGN.md §5; DECISIONS.md D8, G8."""),

  leaf("p4_streaming", "Prefixed interleaved streaming ([adapter#n], colored)", "p4", List("p3_liveask"),
    "engine:codex", "model:o3", "4", "codex", "o3", "stream formatting, medium",
"""### Goal
Default display for parallel agents: prefixed interleaving `[claude#2] ...`, colored per agent (the docker-compose/sbt pattern) — works in every terminal and CI (D9).

### Details
- Tag each spawned agent's stderr lines with a stable `[adapter#n]` prefix and a per-agent color; interleave onto the parent stderr while also writing raw to each log file.

### Acceptance
- Two concurrent fake agents produce readable, correctly-prefixed interleaved output; log files remain un-prefixed raw.

### References
DECISIONS.md D9; DESIGN.md §5."""),

  leaf("p4_panes", "--panes tmux split-window integration", "p4", List("p4_streaming"),
    "engine:codex", "model:o3", "4", "codex", "o3", "shell integration, medium",
"""### Goal
Optional `--panes`: when inside tmux (`$TMUX` set), spawn `tmux split-window 'tail -f <log>'` per agent for real side-by-side windows (D9).

### Details
- Only activate when `$TMUX` is set; otherwise fall back to prefixed interleaving. Zero code to maintain the panes themselves — just `tail -f` the per-agent log files.
- Manual escape hatch (`tail -f .agents/logs/*.log`) must keep working.

### Acceptance
- With `$TMUX` set, panes open per agent; without it, graceful fallback. Guarded so non-tmux/CI never breaks.

### References
DECISIONS.md D9."""),

  leaf("p4_logrot", "Log-file layout & rotation under .agents/logs/", "p4", List("p3_liveask"),
    "engine:codex", "model:o4-mini", "4", "codex", "o4-mini", "trivial file mgmt",
"""### Goal
Define `.agents/logs/` layout and simple rotation so logs don't grow unbounded.

### Details
- Per-purpose/per-agent files `.agents/logs/<name>.log`; rotate by size or run (e.g. keep last N). Document the layout.

### Acceptance
- Rotation keeps at most N files/size; layout documented.

### References
DECISIONS.md D9; DESIGN.md §2."""),

  // ---------- Phase 5 ----------
  leaf("p5_migrate", "Migrate scala-llm-template/Setup.scala onto arrowstep", "p5", List("p1_agentmain", "p0_replayask"),
    "engine:claude", "model:sonnet", "5", "claude", "sonnet", "cross-project Scala refactor, scala-semantic useful",
"""### Goal
First real consumer (G1): replace Setup.scala's `readLine`-based `getAgentAnswers` protocol with `ReplayAsk`, and express Setup as a `Flow`.

### Details
- Model Setup's detect -> ask -> plan -> apply as a `Flow` pipeline (DESIGN §7 shape). Replace interactive stdin (rejected by D1) with the run-to-completion `--agent` protocol + `ReplayAsk`.
- Reuse the basic `Validator`; extend `QuestionKind` only if Setup needs it (defer per Open Questions).
- Wire `AgentMain.run` as the entry point.

### Acceptance
- Setup runs end-to-end via the wire protocol on a sample project dir; produces the same plan/report it did before, driven by `.agents/answers.json`.

### References
DECISIONS.md G1, D1, D3; DESIGN.md §7; ROADMAP Phase 5."""),

  leaf("p5_instruction", "One-paragraph agent instruction in consumer CLAUDE.md", "p5", List("p5_migrate"),
    "engine:agy", "model:gemini-3.5-flash-high", "5", "agy", "Gemini 3.5 Flash (High)", "docs",
"""### Goal
Add the L0 agent-facing paragraph to scala-llm-template's CLAUDE.md so any agent can drive Setup.

### Details
- The paragraph (DESIGN §2): run `<program> --agent`; exit 0 -> read result JSON from stdout; exit 2 -> stdout has `questions`, write answers into `.agents/answers.json` (id->string) and rerun; repeat until exit 0.

### Acceptance
- Instruction present, accurate to the implemented protocol, and self-contained.

### References
DESIGN.md §2; depends on the migration."""),

  leaf("p5_feedback", "Feedback loop — file issues for library gaps found during migration", "p5", List("p5_migrate"),
    "engine:agy", "model:gemini-3.5-flash-high", "5", "agy", "Gemini 3.5 Flash (High)", "analytic/process writeup",
"""### Goal
Capture whatever Setup needed that arrowstep lacked as issues on this repo (close the loop, G1).

### Details
- During/after the migration, list missing capabilities (e.g. richer `QuestionKind`, conditional dialogues, streaming) and open tracking issues with concrete asks. Cross-link to the relevant Open Questions in ROADMAP.

### Acceptance
- A short report + issues filed (or a note that none were needed).

### References
ROADMAP Phase 5, "Open questions"."""),

  // ---------- Phase 6 ----------
  leaf("p6_publish", "Publish to Maven Central", "p6", List("p0_scaffold"),
    "engine:codex", "model:o3", "6", "codex", "o3", "build/publish config",
"""### Goal
Publish arrowstep to Maven Central (`scala-cli publish`, or migrate to sbt if cross-building demands).

### Details
- Coordinates, license, POM metadata, signing; decide scala-cli publish vs sbt based on cross-build needs. Ensure `core`/`runtime` module artifacts are coherent.

### Acceptance
- A published (or dry-run/staged) artifact resolvable by coordinates; release steps documented.

### References
ROADMAP Phase 6; DECISIONS.md D10."""),

  leaf("p6_readme", "README quickstart + mdoc-checked docs", "p6", List("p5_migrate"),
    "engine:agy", "model:gemini-3.5-flash-high", "6", "agy", "Gemini 3.5 Flash (High)", "docs, large-context",
"""### Goal
README quickstart and mdoc-checked documentation (examples compile).

### Details
- Quickstart: add dependency, write a `Flow`, run with `--agent`. Use mdoc (docs module) so code snippets are compiled/verified. Pull the real example from Phase 0/5.

### Acceptance
- `mill docs` / mdoc passes; README quickstart is runnable and accurate.

### References
ROADMAP Phase 6; PROJECT.md (mdoc-docs)."""),

  leaf("p6_compat", "Wire-protocol version/compat policy", "p6", List("p1_codecs"),
    "engine:claude", "model:sonnet", "6", "claude", "sonnet", "spec/design reasoning",
"""### Goal
Define a version/compatibility policy for the L0 wire protocol — it is a spec other languages could implement.

### Details
- Versioning scheme for the JSON message shapes; compatibility rules (additive vs breaking); how a `version` is signalled (field or doc contract). Keep it minimal but explicit so external implementers can rely on it.

### Acceptance
- Written policy in docs; golden tests (p1_codecs) referenced as the compatibility oracle.

### References
ROADMAP Phase 6; DESIGN.md §2; DECISIONS.md D1.""")
)

val order: List[Node] = root :: phaseEpics ::: leaves

// ============================ GH HELPERS ============================
def gh(args: String*): ujson.Value =
  val r = os.proc("gh", args).call(check = false)
  if r.exitCode != 0 then
    System.err.println(s"gh ${args.mkString(" ")} FAILED:\n${r.err.text()}")
    sys.error("gh call failed")
  val out = r.out.text().trim
  if out.isEmpty then ujson.Null else ujson.read(out)

def mklabel(name: String, color: String, desc: String): Unit =
  val create = os.proc("gh", "label", "create", name, "--repo", s"$Owner/$Repo",
    "--color", color, "--description", desc).call(check = false)
  if create.exitCode != 0 then
    os.proc("gh", "label", "edit", name, "--repo", s"$Owner/$Repo",
      "--color", color, "--description", desc).call(check = false)
  ()

// ============================ LABELS ============================
mklabel("engine:codex", "1d76db", "worker engine: codex")
mklabel("engine:agy", "5319e7", "worker engine: agy")
mklabel("engine:claude", "d93f0b", "worker engine: claude (reserved for hardest Scala)")
mklabel("model:o4-mini", "c2e0c6", "codex o4-mini (cheap/fast)")
mklabel("model:o3", "0e8a16", "codex o3 (strong reasoning)")
mklabel("model:sonnet", "fbca04", "claude sonnet (mid)")
mklabel("model:opus", "b60205", "claude opus (costliest, hardest)")
mklabel("model:gemini-3.1-pro-high", "6f42c1", "agy Gemini 3.1 Pro (High)")
mklabel("model:gemini-3.5-flash-high", "bfdadc", "agy Gemini 3.5 Flash (High)")
mklabel("depth:0", "000000", "tree depth 0 (root)")
mklabel("depth:1", "333333", "tree depth 1 (phase)")
mklabel("depth:2", "999999", "tree depth 2 (leaf)")
mklabel("type:epic", "5319e7", "epic / parent")
mklabel("type:leaf", "0366d6", "leaf task")
(0 to 6).foreach(p => mklabel(s"phase:$p", "ededed", s"Roadmap Phase $p"))

// ============================ PASS 1: create issues ============================
val tmp = os.temp.dir(prefix = "arrowstep-tasks")

// created: key -> (number, dbId)
val created: Map[String, (Int, Long)] =
  order.foldLeft(Map.empty[String, (Int, Long)]) { (acc, n) =>
    val bodyFile = tmp / s"body_${n.key}.md"
    os.write.over(bodyFile, n.body)
    val labels = List(s"depth:${n.depth}", s"type:${n.nodeType}") ++
      (if n.phase != "all" then List(s"phase:${n.phase}") else Nil) ++
      (if n.engineLabel.nonEmpty then List(n.engineLabel) else Nil) ++
      (if n.modelLabel.nonEmpty then List(n.modelLabel) else Nil)
    val labelArgs = labels.flatMap(l => List("-f", s"labels[]=$l"))
    val args = List("api", "--method", "POST", s"$Api/issues",
      "-f", s"title=${n.title}", "-F", s"body=@$bodyFile") ++ labelArgs
    val resp = gh(args*)
    val num = resp("number").num.toInt
    val id  = resp("id").num.toLong
    println(s"created #$num  ${n.key}  (${n.title})")
    acc + (n.key -> (num, id))
  }

def num(key: String): Int = created(key)._1

// ============================ PASS 2: native sub-issue links ============================
order.filter(_.parent.nonEmpty).foreach { n =>
  val parentNum = num(n.parent)
  val childId = created(n.key)._2
  gh("api", "--method", "POST", s"$Api/issues/$parentNum/sub_issues", "-F", s"sub_issue_id=$childId")
  println(s"sub-issue: #${num(n.key)} -> parent #$parentNum")
}

// ============================ PASS 3: append Depends-on / Blocks ============================
val blocks: Map[String, List[String]] =
  leaves.foldLeft(Map.empty[String, List[String]]) { (acc, n) =>
    n.deps.foldLeft(acc)((a, d) => a + (d -> (a.getOrElse(d, Nil) :+ n.key)))
  }

leaves.foreach { n =>
  val depStr = if n.deps.isEmpty then "none" else n.deps.map(d => s"#${num(d)}").mkString(", ")
  val blkStr = blocks.getOrElse(n.key, Nil) match
    case Nil => "none"
    case bs  => bs.map(b => s"#${num(b)}").mkString(", ")
  val extra =
    s"""
       |
       |---
       |**Parent:** #${num(n.parent)}
       |**Depends on:** $depStr
       |**Blocks:** $blkStr""".stripMargin
  val bodyFile = tmp / s"body_${n.key}.md"
  os.write.over(bodyFile, n.body + extra)
  gh("api", "--method", "PATCH", s"$Api/issues/${num(n.key)}", "-F", s"body=@$bodyFile")
  println(s"links: #${num(n.key)} deps[$depStr] blocks[$blkStr]")
}

println(s"\nDONE. Root epic: #${num("root")}")

/*</script>*/ /*<generated>*//*</generated>*/
}

object create$minustask$minustree_sc {
  private var args$opt0 = Option.empty[Array[String]]
  def args$set(args: Array[String]): Unit = {
    args$opt0 = Some(args)
  }
  def args$opt: Option[Array[String]] = args$opt0
  def args$: Array[String] = args$opt.getOrElse {
    sys.error("No arguments passed to this script")
  }

  lazy val script = new create$minustask$minustree$_

  def main(args: Array[String]): Unit = {
    args$set(args)
    val _ = script.hashCode() // hashCode to clear scalac warning about pure expression in statement position
  }
}

export create$minustask$minustree_sc.script as `create-task-tree`

