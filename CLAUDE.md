# CLAUDE.md — arrowstep

Central project context and coding rules are imported below. Read them; put project facts in
`PROJECT.md`, not here.

@PROJECT.md

## Claude-specific notes

- Follow the wire-protocol discipline when touching runtime code: protocol JSON on **stdout only**,
  everything human-readable on **stderr** (D1, D9).
- Do not add "Co-Authored-By" trailers to commits.
- Keep command output lean; prefix with `rtk` when installed (scala-rules §19).