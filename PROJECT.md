# arrowstep — Central Agent Context

Single source of truth for any agent (Claude, Codex, Gemini/agy, Cursor) working here.
Tool-specific files (`CLAUDE.md`, `AGENTS.md`, `GEMINI.md`, `.cursorrules`) include this file
plus [`scala-rules.md`](scala-rules.md); keep project facts **here**, not duplicated in them.

## What this is

A Scala 3 library for **typed, compiler-checked, replayable dialogues between programs and coding
agents** (Claude Code, Gemini CLI, aider, Codex, ...). One dialogue = one lawful `cats` arrow
composition. Who leads (agent or program), which LLM answers, and how messages travel are runtime
plug-ins behind typed boundaries.

State: **planning / early scaffold** — design docs done, core code being built (Roadmap Phase 0).

## Read these first (the "why" and "how")

| Doc | Purpose |
|---|---|
| [docs/DECISIONS.md](docs/DECISIONS.md) | **Read first.** ADRs (D1–D10), goals G1–G9, rejected roads. Most "why not X?" answered here. |
| [docs/DESIGN.md](docs/DESIGN.md) | The 4-layer architecture (L0 wire protocol → L3 consumer flow), core types, runtime. |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phased plan; each phase ends runnable. Current: Phase 0 skeleton. |
| [scala-rules.md](scala-rules.md) | Coding rules (pure FP, cats-effect, MUnit, wartremover, ScalaSemantic MCP). |

## Architecture in one screen

- **L0 wire protocol** — agent-facing bash contract: run `<program> --agent`; exit `0`=done,
  exit `2`=need input; exactly one JSON object on **stdout**, all human output on **stderr**.
  Answers written by agent to `.agents/answers.json`.
- **L1 core** (pure, tagless-final, cats Arrows) — `Flow[F, A, B]` (opaque `Kleisli`, `ArrowChoice`);
  `Question`/`Answers`/`ValidAnswers`/`Problem`/`ProgramSays`; `Ask[F]` (the typed agent gap);
  `Dialogue.askUntilValid`.
- **L2 runtime** (cats-effect edge) — `ReplayAsk` (agent leads), `LiveAsk` (program leads),
  `StubAsk` (tests); `AnswerLog`, `ProtocolJson`, `SessionStore`, `AdapterRegistry`, `AgentMain`.
- **L3 consumer** — one arrow pipeline value = the whole dialogue spec, checked by the compiler.

`ValidAnswers` is opaque, constructible only by a `Validator` → unvalidated agent output physically
cannot reach the effects stage (compile error, not convention).

## Stack & layout

- Scala 3.8.4, Typelevel: `cats-core`, `cats-effect`. Tests: MUnit (+ shapeless3 deriving).
- Compiler plugins: **wartremover** (Unsafe warts), **stainless** (formal verification).
- `-Ysemanticdb` **on** (build.sc + project.scala) → ScalaSemantic MCP works after a compile.
- Two build entry points: **Mill** (`build.sc`, module `app`, `docs`) and **scala-cli**
  (`project.scala`). Scripts under `scripts/` are scala-cli. mdoc docs in `mdoc-docs/`.
- Package split (per D10): `core` (no effects-runtime coupling), `runtime`, `example`.

## Common commands

```bash
mill app.compile        # compile core module (also refreshes SemanticDB for ScalaSemantic MCP)
mill app.test           # run MUnit tests
mill prePush            # compile + test (the pre-push gate)
scala-cli compile .     # scala-cli path (project.scala)
```

> Keep command output lean (scala-rules §19): pipe to `grep -i error` / `head`, redirect success
> logs to `/dev/null`. Prefix with `rtk` when available.

## ScalaSemantic MCP

Registered as `scala-semantic` in [.mcp.json](.mcp.json). Compile before use so SemanticDB exists.
For `.scala` source reading/searching/analysis, prefer its tools over `grep`/`cat`/`rg`
(scala-rules §18). Launcher install (once, if missing):

```bash
curl -fsSL https://raw.githubusercontent.com/MercurieVV/ScalaSemantic/master/scripts/install.sh | sh
```