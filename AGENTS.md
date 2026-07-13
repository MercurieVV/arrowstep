# AGENTS.md — arrowstep (Codex / generic)

This is the entry point for Codex and other AGENTS.md-aware tools. Codex has no file-include
directive, so **read these two files at the start of every task** — they are the real spec:

1. [`PROJECT.md`](PROJECT.md) — central project context (what arrowstep is, architecture,
   stack, build commands, doc index).

Then read `docs/DECISIONS.md` before proposing any architectural change.

## Response style

- Be terse. Don't restate the task or narrate a plan before editing. Output the edit/diff directly.
- No summary after the edit unless asked.

## Non-negotiables

- Pure FP; no `var`/`null`/`throw`; errors as `Either`/`Try`/effects. No `unsafeRunSync`.
- Program to abstract typeclasses (`Monad`/`Sync`/`Concurrent`), not concrete `IO`.
- Runtime code: protocol JSON on **stdout only**, human output on **stderr** (D1, D9).
- Prefer the `scala-semantic` MCP tools over text/shell tools on `.scala` files. Compile first.
- Build: `mill app.compile` / `mill app.test` / `mill prePush`. Keep output lean.