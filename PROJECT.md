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
- SemanticDB **on** in Mill modules (`build.mill`), `project.scala`, and standalone
  `scripts/*.scala` / `scripts/*.sc` → ScalaSemantic MCP works after a compile.
- Two build entry points: **Mill** (`build.mill`, module `app`, `docs`) and **scala-cli**
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

## Scripts — prefer `scripts/` over ad-hoc commands

Common chores have a committed scala-cli script under `scripts/`. **Use these instead of hand-rolling
equivalent shell** — they detect the build tool (sbt / mill / scala-cli) and encode project
conventions (e.g. worktrees live at a visible path, never inside a hidden `.`-dir). Run with
`scala-cli run scripts/<name>`.

| Script | When to use it | Invocation |
|---|---|---|
| `worktree-start.scala` | Start a task in an isolated git worktree branched off the default branch. | `scala-cli run scripts/worktree-start.scala -- <branch>` |
| `worktree-finish.scala` | Clean out a finished task worktree after the PR flow (auto-detects the branch from the path, or pass it). Skips local merge by default; use `--merge-local` only for non-PR local branch flows. | `scala-cli run scripts/worktree-finish.scala -- [--merge-local] [--delete-remote] [<branch>]` |
| `git-pre-commit.scala` | Pre-commit gate: scalafmt / scalafix checks (skips if neither config is present). | `scala-cli run scripts/git-pre-commit.scala` |
| `git-pre-push.scala` | Pre-push gate: compile + test via the detected build tool (mill `prePush` here). | `scala-cli run scripts/git-pre-push.scala` |
| `version-bump.scala` | Bump the version in the build file. | `scala-cli run scripts/version-bump.scala -- <major\|minor\|patch>` |
| `create-task-tree.sc` | (Re)create the GitHub issue task tree as native sub-issues. Labels idempotent; **rerunning duplicates issues**. Needs `gh` authed with `repo` scope. | `scala-cli run scripts/create-task-tree.sc` |

Common PR task flow: `worktree-start.scala -- <issue>`, implement in `.worktrees/<issue>`, run
`git-pre-push.scala` or `mill prePush`, push/create/merge the PR with `gh`, post the issue output
comment, then run `worktree-finish.scala -- <issue>` to remove the local worktree and branch. Use
`worktree-finish.scala -- --merge-local <branch>` only when intentionally skipping the PR flow.

## ScalaSemantic MCP

@scala-rules.md [`scala-rules.md`](scala-rules.md)
@SCALA_SEMANTIC_RULES.md [`SCALA_SEMANTIC_RULES.md`](SCALA_SEMANTIC_RULES.md)

Codex loads the project-scoped ScalaSemantic MCP server from `.codex/config.toml` when this repo is
trusted. `.mcp.json` is kept for other MCP-aware tools that read that file directly.

```bash
curl -fsSL https://raw.githubusercontent.com/MercurieVV/ScalaSemantic/master/scripts/install.sh | sh
```
