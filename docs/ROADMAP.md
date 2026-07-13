# Roadmap

Phases are ordered so that every phase ends with something runnable. Current state: **Phase 0
skeleton in progress**.

## Phase 0 — Skeleton
- [x] Project scaffold (scala-cli, Scala 3, cats-core / cats-effect / os-lib / ujson)
- [x] `core.Flow` — opaque Kleisli arrow, `>>>` `&&&` `***` `first`, `ArrowChoice` instance
- [x] `core` vocabulary — `Question`, `QuestionKind`, `Answers`, `ValidAnswers`, `Problem`,
      `ProgramSays`, `AskInput`
- [x] `core.Ask`, `core.Validator` (+ basic choice/free-text validator), `core.Dialogue.askUntilValid`
- [x] `runtime.AnswerLog` (file-backed), `runtime.StubAsk`
- [x] `runtime.ReplayAsk`
- [x] `runtime.ProtocolJson` (render `ProgramSays` to wire JSON)
- [x] `runtime.SessionStore`, `runtime.AgentAdapter`/registry types, `LiveAsk` stub
- [x] Example consumer flow + munit test executing the flow with `StubAsk`
- [x] Design docs (DESIGN.md, DECISIONS.md, this file)

## Phase 1 — Protocol hardening
- [x] JSON codecs derived/centralized for every L0 message; golden-file tests for wire shapes
- [x] `AgentMain` entry-point helper: flag parsing (`--agent`, `--answers`, `--fresh`,
      `--resume-session`, `--adapter`), `ProgramSays → exit code`, stdout/stderr discipline
- [x] Inline `--answers` accepted and persisted into the answer log (D2)
- [x] `Rejected` loop end-to-end: validator problems re-emitted with offending questions

## Phase 2 — Replay completeness
- [x] `Cached` step helper: run-once effects whose results are stored in the answer log
      (Maven version lookups etc. — the D3 determinism requirement, made ergonomic)
- [x] Replay determinism test-kit: run a flow twice against the same log, assert identical
      question sequences
- [x] Answer-log hygiene: stale answers for removed questions, `--reset`

## Phase 3 — Program-led mode (LiveAsk)
- [x] `AdapterRegistry`: load `.agents/adapters.json`, ship presets (claude, gemini; PRs welcome)
- [x] `LiveAsk` implementation: template expansion, process spawn, stream to stderr + log file,
      final-JSON parse, session persistence (D5, D6)
- [x] Session lifecycle: `--fresh`, `--resume-session`, purpose-scoped ids in `sessions.json`
- [x] Context briefs: `AskInput.context` rendered into the prompt for fresh sessions

## Phase 4 — Parallelism & observability
- [x] Parallel `ask` combinator (`parAsk` over independent question groups / multiple adapters)
- [x] Prefixed interleaved streaming (`[claude#2]`, per-agent color) as default display
- [x] `--panes`: tmux split-window integration when `$TMUX` is set
- [x] Log-file layout & rotation under `.agents/logs/`

## Phase 5 — First real consumer
- [ ] Migrate `scala-llm-template/Setup.scala` onto arrowstep (replace `getAgentAnswers`
      readLine protocol with `ReplayAsk`; express Setup as a `Flow`)
- [ ] The one-paragraph agent instruction added to that project's CLAUDE.md
- [ ] Feedback loop: whatever Setup needed that the library lacked → issues here

## Phase 6 — Release
- [ ] Publish to Maven Central (`scala-cli publish` or migrate to sbt if cross-building demands)
- [ ] README quickstart; mdoc-checked docs
- [ ] Version/compat policy for the wire protocol (L0 is a spec other languages could implement)

## Open questions (deliberately deferred)
- Conditional dialogues: answer → new question round. Replay supports it naturally (the flow just
  asks more); needs a worked example and possibly `ArrowChoice` sugar (`|||`) at L3.
- Question typing beyond strings (numbers, multi-select) — extend `QuestionKind` when a consumer
  needs it.
- Streaming partial agent answers (fs2 at L2 only, never in core) — only if a real consumer wants
  progressive output.
- Windows support for the tmux/`--panes` path (interleaving works everywhere already).
