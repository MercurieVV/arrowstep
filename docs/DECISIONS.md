# Decisions, Goals & Needs

This document is the reasoning memory of the project. Every architectural decision below was
reached through explicit discussion (see git history of `scala-llm-template`'s Setup.scala for the
first prototype context). When extending the library, read this first — most "why not X?" questions
are already answered here, including the alternatives that were considered and rejected.

---

## Goals & Needs

- **G1 — Reusable library.** A framework any Scala program can use to hold structured dialogues
  with coding agents (Claude Code, Gemini CLI, aider, Codex, ...). First consumer: the
  `Setup.scala` project generator in `scala-llm-template`.
- **G2 — Birdview visibility.** The entire program↔agent flow must be readable top-to-bottom in
  one place, as one value. No control flow hidden inside interpreters or scattered across files.
- **G3 — Determinism.** As deterministic as possible. Nondeterminism (network, time, randomness)
  is quarantined and cached so replays walk identical paths.
- **G4 — Compiler-checked correctness before run.** Step wiring, ordering, and message schemas are
  verified by the type checker. A misordered flow or a drifted message shape must not compile.
- **G5 — Runnable spec.** The same flow value executes in three modes: live (real agent), replay
  (suspend/resume across process runs), stubbed (tests, no agent, no side effects). The
  specification *is* the program, not documentation about it.
- **G6 — Leader-agnostic.** The same flow works whether the agent drives the program or the
  program drives the agent. Leadership is a runtime plug-in choice, not an architecture fork.
- **G7 — Any agent, switchable on the fly.** No coupling to one LLM CLI. Adapters are
  configuration; switching agents between two `ask` steps is legal. Top CLIs ship preconfigured.
- **G8 — Parallel agents, observable.** Multiple agents may run concurrently; the human must be
  able to follow all their output in a readable way.
- **G9 — Highest abstraction level uses cats Arrows and custom abstract types.** (Explicit user
  requirement.) The top level abstracts from all details: effect type (`F[_]`, tagless final),
  agent identity, leadership, transport. Concrete `IO`, JSON, process-spawning appear only at the
  runtime edge.

---

## Decisions (ADR style)

### D1 — Transport: run-to-completion CLI protocol

Every program↔agent exchange is a shell command that runs and **exits**.
Exit code `0` = done; exit code `2` = need input. Exactly one JSON object on **stdout**; all
human/diagnostic output on **stderr**.

*Why:* the lowest common denominator of every coding-LLM CLI is: run a command, read stdout,
write a file, branch on exit code. Nothing else is portable.

*Rejected:* interactive stdin (`readLine()` mid-run, the original Setup.scala prototype). Agents'
shell tools are built around run-to-completion; a process waiting on stdin produces timeouts and
lost prompts.

### D2 — Answers travel via persisted file, not pipes

Answers land in `.agents/answers.json`. An inline `--answers '<json>'` argument is accepted as a
convenience, but is immediately persisted to the same file by the program itself.

*Why:* (1) replay (D3) needs answers persisted across runs anyway; (2) shell-quoting large JSON
inline is a classic source of LLM-mangled input; (3) a file on disk is a post-mortem record of
exactly what the agent said. One-shot stdin pipes are acceptable in principle; interactive stdin
is not (D1).

### D3 — Resume by replay, never by serialized continuations

The program never saves "where it was". On every invocation it re-executes its flow from the
start; each `ask` step is fed from the answer log; the first unanswered question batch is emitted
(exit 2) and the process ends. Position in the dialogue is *recomputed* as a pure function of the
answer log — the event-sourcing / Temporal / Durable-Functions trick.

*Why:* lawful composition (`>>>`, `flatMap`) holds Scala closures, and closures don't serialize.
Suspending mid-chain across a process boundary is therefore impossible directly; replay makes the
suspension semantic instead of physical.

*Requirements this imposes:* (a) same answers ⇒ same questions in same order (determinism);
(b) world-changing effects happen after all questions are answered, or are idempotent;
(c) nondeterministic reads (e.g. "latest Scala version from Maven") are performed once and their
result stored into the log, so replays see a constant.

*Rejected:* Free monad with persisted continuations (`FlatMapped` holds functions — not
serializable); keeping the process alive (violates D1).

### D4 — Leadership is decided by the entry point, not by the design

"Who calls whom" is not an architectural commitment. The flow is written once; the `Ask`
implementation plugged into it decides leadership:

| Implementation | Leader | Mechanism |
|---|---|---|
| `ReplayAsk` | agent | look up answer log; missing → emit `NeedInput`, exit 2; agent reruns |
| `LiveAsk`   | program | spawn agent CLI (`claude -p --resume <id>`), parse its JSON reply |
| `StubAsk`   | nobody | canned answers; tests and dry runs |

*Why:* in an interactive coding session the informed agent is already outside and must lead
(the program cannot reach the parent session — see D5). In CI / standalone runs there is no outer
agent, so the program must summon one. Both scenarios are real; the typed flow makes supporting
both a ~30-line difference instead of a fork.

### D5 — Programs own their agent sessions; never touch the parent's

When the program leads (LiveAsk), it creates and owns **its own** agent session, stored in
`.agents/sessions.json` (`purpose → session-id`). It never resumes the session of the agent that
invoked it.

*Why:* resuming the parent's session while the parent is blocked waiting on the program forks the
transcript into a second headless conversation — permission prompts reach nobody, and nothing
flows back to the session the human watches. Own-session design also makes nesting uniform at any
depth: each layer owns its sessions and passes ids down explicitly.

*Corollary:* a fresh sub-session knows nothing of the parent conversation, so context is passed
**explicitly as data** (the `context` fields of `AskInput`/`Question`). This is a feature: the
context contract becomes visible, typed data instead of ambient conversation.
The caller controls continuity with `--fresh` / `--resume-session <id>`.

### D6 — Agent adapters are configuration

`.agents/adapters.json` holds per-CLI command templates:

```json
{
  "claude": {
    "new":    ["claude", "-p", "--output-format", "json", "{prompt}"],
    "resume": ["claude", "-p", "--resume", "{session}", "--output-format", "json", "{prompt}"]
  },
  "gemini": { "new": ["gemini", "..."], "resume": ["gemini", "..."] }
}
```

`LiveAsk` is parameterized by an adapter; switching agents on the fly = choosing a different
adapter for the next `ask`. Top coding CLIs ship preconfigured; users add their own.

### D7 — The agent is a typed boundary; its judgment is fenced at runtime

The agent execution gap is typed as `AskInput => F[Answers]` (an ordinary effectful function /
Kleisli arrow). The compiler checks everything up to that boundary: wiring, ordering, schemas.
What it cannot check — whether the LLM's answers are *good* — is fenced by the runtime
complement: a `Validator` and the `Rejected` re-ask loop.

Type-level enforcement: `ValidAnswers` is an opaque type constructible **only** by a `Validator`.
Unvalidated agent output physically cannot reach the apply/effects stage — that is a compile
error, not a convention.

### D8 — Core abstraction: cats Arrows over opaque types, tagless final

The top-level abstraction is `Flow[F[_], A, B]` — an opaque type over `cats.data.Kleisli`,
composed with arrow combinators (`>>>`, `&&&`, `***`). Effect type is abstract (`F[_]` with
capability constraints); `cats.effect.IO` appears only in the runtime module and examples.

*Why arrows here:* the flow is a pipeline of typed steps where composition order *is* the
birdview; arrow combinators make that pipeline a first-class, inspectable value, and the
`ArrowChoice` instance comes free from Kleisli. Independent questions are batched into a single
`ask` (one agent round-trip) — the static-analysis benefit that motivated applicative/arrow style,
without per-question ceremony.

*Noted alternative:* plain for-comprehensions have the same semantics; arrows are the chosen
surface per G9.

### D9 — Observability: protocol on stdout, everything else visible elsewhere

- **stdout** is reserved exclusively for the one protocol JSON object (machines parse it).
- Live agent output streams to **stderr** and to per-agent log files `.agents/logs/<name>.log`
  (e.g. via `claude -p --output-format stream-json --verbose`).
- Parallel agents: default display is **prefixed interleaving** (`[claude#2] ...`, colored per
  agent — the docker-compose/sbt/cargo pattern; works in every terminal and CI).
- Optional `--panes`: when inside tmux (`$TMUX` set), spawn `tmux split-window 'tail -f <log>'`
  per agent — real side-by-side windows, zero code to maintain. zellij/kitty equivalents possible.
- Manual escape hatch always works: `tail -f .agents/logs/*.log`.

*Rejected for now:* custom in-process TUI (e.g. VirtusLab `tui-scala`). Prettiest, most work,
fights the stdout discipline. Revisit only if the simpler modes prove insufficient.

### D10 — Build & stack

Scala 3, scala-cli, dependencies: `cats-core`, `cats-effect`, `os-lib`, `ujson`. Library name
`agent-arrows`. Modules by package (`core` = no effects-runtime coupling, `runtime`, `example`).

---

## Rejected roads (summary, so they are not re-litigated)

| Alternative | Why rejected |
|---|---|
| Interactive stdin protocol | agents run commands to completion; fragile (D1) |
| Script executes agent inline, sharing parent session | forks/strands the parent transcript; headless permission prompts (D5) — refined into LiveAsk with own sessions |
| Serialized continuations (Free monad persisted mid-chain) | closures don't serialize; replay instead (D3) |
| fs2 as flow backbone | streams solve in-process streaming; this flow crosses process lifetimes |
| Raw `Arrow` encoding without opaque wrapper | tuple-plumbing noise; opaque `Flow` + Kleisli gives lawful instances with readable surface (D8) |
| Custom TUI for parallel output | overkill; terminal multiplexers already exist (D9) |
