# GEMINI.md — arrowstep

Context files for Gemini CLI / agy. Central project context and coding rules are imported below.
Put project facts in `PROJECT.md`, not here.

@PROJECT.md

## Gemini / agy notes

- Read `docs/DECISIONS.md` before any architectural change.
- Use the `scala-semantic` MCP server for `.scala` file reading/searching/analysis; compile
  (`mill app.compile`) first so SemanticDB is present. Avoid raw `grep`/`cat` on `.scala`.
- Runtime code: protocol JSON on **stdout only**, human output on **stderr** (D1, D9).
- agy worktrees must live at a **visible** path (e.g. `./worktrees/<branch>`), never under a
  hidden `.`-prefixed dir — agy silently falls back to its own scratch dir otherwise.