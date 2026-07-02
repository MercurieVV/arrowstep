# agent-arrows — Design

A Scala library for **typed, compiler-checked, replayable dialogues between programs and coding
agents** (Claude Code, Gemini CLI, aider, ...). The flow of a dialogue is one visible, lawful
arrow composition; who leads (agent or program), which LLM answers, and how messages travel are
runtime plug-ins behind typed boundaries.

Read [DECISIONS.md](DECISIONS.md) first for the why; this document is the how.

---

## 1. The picture

```
┌─ L3  consumer program (e.g. Setup.scala) ─────────────────────────────┐
│   val flow: Flow[F, ProjectDir, Report] =                             │
│     inspect >>> (Flow.id &&& askValidated) >>> makePlan >>> applyPlan │
└───────────────────────────────────────────────────────────────────────┘
┌─ L2  runtime (cats-effect edge) ──────────────────────────────────────┐
│   ReplayAsk   — agent leads: answer log lookup | NeedInput + exit 2   │
│   LiveAsk     — program leads: adapter cmd + own session (--resume)   │
│   StubAsk     — tests: canned answers, no agent                       │
│   ProtocolJson, AnswerLog, SessionStore, AdapterRegistry, AgentLogs   │
└───────────────────────────────────────────────────────────────────────┘
┌─ L1  core (pure, tagless final, cats Arrows) ─────────────────────────┐
│   Flow[F, A, B]   opaque Kleisli + ArrowChoice; >>> &&& *** first     │
│   Question / Answers / ValidAnswers / Problem / ProgramSays           │
│   Ask[F]  (the agent gap, typed)   Validator[F]   Dialogue            │
└───────────────────────────────────────────────────────────────────────┘
┌─ L0  wire protocol (language-agnostic spec) ──────────────────────────┐
│   exit 0 = done · exit 2 = need input · one JSON on stdout            │
│   answers file · sessions file · adapters file · log files            │
└───────────────────────────────────────────────────────────────────────┘
```

An agent (any coding LLM) only ever sees L0 — plain bash. A consumer program only writes L3 —
one arrow pipeline. Everything between is this library.

## 2. The wire protocol (L0)

The whole agent-facing contract, describable in one paragraph inside any project's CLAUDE.md:

> Run `<program> --agent`. If it exits `0`, read the result JSON from stdout — done. If it exits
> `2`, stdout contains a JSON object with `questions`; write your answers into
> `.agents/answers.json` (object mapping question `id` → answer string) and run the same command
> again. Repeat until exit `0`.

### Messages (stdout, exactly one JSON object per run)

```jsonc
// exit 2 — need input
{ "status": "need-input",
  "context": "Configuring Scala project ./x",          // brief for fresh/foreign agents
  "questions": [
    { "id": "web-server", "text": "Enable Web Server?",
      "kind": "choice", "allowed": ["yes", "no"],
      "default": "no", "current": "no", "context": null } ] }

// exit 2 — answers present but invalid; fix and rerun
{ "status": "rejected",
  "problems": [ { "questionId": "web-server", "message": "'maybe' not in [yes, no]" } ],
  "questions": [ /* the offending questions, re-stated */ ] }

// exit 0 — done
{ "status": "done", "result": { /* consumer-defined */ } }
```

### Files (all under `.agents/`, the directory agents already know)

| File | Owner | Purpose |
|---|---|---|
| `answers.json` | agent writes, program reads | the answer log; also the replay memory (D3) |
| `sessions.json` | program | `purpose → session-id` for program-led (LiveAsk) mode (D5) |
| `adapters.json` | user/project | per-CLI command templates (D6) |
| `logs/*.log` | program | live streams of each spawned agent (D9) |

Stdout carries protocol JSON **only**; human-readable progress goes to stderr.

## 3. Core types (L1)

### `Flow[F, A, B]` — the arrow

```scala
opaque type Flow[F[_], -A, B] = Kleisli[F, A, B]
```

One typed step of a dialogue pipeline. Lawful `ArrowChoice` instance (inherited from Kleisli, so
the laws are cats' laws, not ours). Combinators: `>>>` (sequence), `&&&` (fan-out), `***`
(parallel pairs), `first`/`second`, `Flow.id`, `Flow.lift` (pure function), `Flow.apply`
(effectful function). Consumers compose the birdview from these; the composition *is* the
specification and it is checked by the compiler: `applyPlan` cannot precede `askValidated`
because the types don't connect.

### The dialogue vocabulary

```scala
final case class Question(id, text, kind, default, current, context)
enum QuestionKind { FreeText; Choice(allowed) }
final case class Problem(questionId, message)

opaque type Answers      = Map[String, String]   // raw agent output
opaque type ValidAnswers = Map[String, String]   // constructible ONLY by a Validator (D7)

enum ProgramSays[+R] { NeedInput(...); Rejected(...); Done(result) }  // exit code derivable
```

`ProgramSays` is the L0 message set as an ADT — the birdview diagram encoded as a type. Adding a
protocol state means adding a case, and every interpreter fails to compile until it handles it.

### `Ask[F]` — the agent gap

```scala
trait Ask[F[_]]:
  def apply(input: AskInput): F[Answers]     // AskInput = questions + context brief
```

This is the *typed hole where the LLM sits* (D7). Everything about "who is the agent, who leads,
how do bytes travel" hides behind this one signature. The core never knows.

### `Dialogue.askUntilValid` — the fence around LLM judgment

```scala
def askUntilValid[F[_]: Monad](ask: Ask[F], validator: Validator[F])
    : Flow[F, AskInput, ValidAnswers]
```

`ask >>> validate`, looping on rejection (re-asking with problems attached). The only factory of
`ValidAnswers`, hence the only door to the effects stage.

## 4. Runtime (L2)

### ReplayAsk — agent leads (interactive coding sessions)

```
apply(input):
  log ← read .agents/answers.json
  all questions answered in log → return Answers      (replay: instant, pure)
  otherwise → emit ProgramSays.NeedInput on stdout, exit(2)
```

The program re-runs from scratch each invocation (D3). Steps before the first missing answer
replay in microseconds; the process advances one question-batch per run, typically completing in
two runs. Determinism contract on the consumer: same answers ⇒ same questions; effects last;
nondeterministic reads cached into the log.

### LiveAsk — program leads (standalone / CI / cron)

```
apply(input):
  adapter  ← AdapterRegistry.resolve(requested | default)
  session  ← SessionStore.get(purpose)                    // .agents/sessions.json
  cmd      ← session.fold(adapter.new)(adapter.resume)    // templates from adapters.json
  spawn cmd, stream its output → stderr + .agents/logs/<purpose>.log
  parse final JSON → Answers; persist session id
```

Own session per purpose, never the parent's (D5). Caller flags: `--fresh`, `--resume-session <id>`,
`--adapter <name>`.

### StubAsk — nobody leads (tests)

Canned `Answers`. Combined with a no-op effects stage this executes the entire flow as a pure
value — the "runnable specification" goal: assert on the produced `Plan` without touching disk or
any agent.

### Entry-point helper

`AgentMain.run(flow, mode)` — parses `--agent`/`--autonomous`/flags, selects the Ask
implementation, runs the flow, renders `ProgramSays` to stdout, maps it to the exit code. A
consumer's `main` is ~5 lines.

## 5. Parallel agents

Independent question groups may consult different agents concurrently: `parMapN`/`parTraverse`
over `ask` calls at L2 (the arrow layer is unchanged — `&&&` of flows whose runtime happens to
parallelize). Observability per D9: each spawned agent gets a log file and a colored
`[adapter#n]` prefix on the interleaved stderr stream; `--panes` upgrades to tmux split panes
when available.

## 6. What the compiler checks — and what it can't

| Checked at compile time | Enforced at runtime instead |
|---|---|
| step wiring & order (types must connect) | quality of LLM answers (Validator + Rejected loop) |
| every protocol state handled (exhaustive `ProgramSays`) | agent actually rerunning the command (it's a bash loop) |
| message schema = ADT, JSON derived from it (no drift) | determinism contract of consumer steps (documented; test helper planned) |
| unvalidated answers cannot reach effects (`ValidAnswers` opaque) | |
| purity of decision steps (`Flow.lift` takes `A => B`, no effect possible) | |

## 7. Consumer example (the birdview, L3)

```scala
val inspect      : Flow[IO, ProjectDir, Detected]
val questionsFor : Flow[IO, Detected, AskInput]              // pure, Flow.lift
val askValidated : Flow[IO, AskInput, ValidAnswers]          // Dialogue.askUntilValid(ask, validator)
val makePlan     : Flow[IO, (Detected, ValidAnswers), Plan]  // pure, Flow.lift — deterministic by type
val applyPlan    : Flow[IO, Plan, Report]                    // the ONLY world-writing step

def flow(ask: Ask[IO]): Flow[IO, ProjectDir, Report] =
  inspect
    >>> (Flow.id[IO, Detected] &&& (questionsFor >>> askValidated))
    >>> makePlan
    >>> applyPlan
```

Five lines = the whole dialogue. Swap `ask` to change leadership; swap nothing else.
