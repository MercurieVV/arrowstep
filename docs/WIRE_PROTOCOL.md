# Wire Protocol

arrowstep's L0 protocol is the stable contract between a consumer program and any coding agent.
It is intentionally small enough for non-Scala tools to implement.

## Process Contract

Run the consumer command with `--agent`.

- Exit `0`: the dialogue is complete. Stdout contains one JSON object with `"status": "done"`.
- Exit `2`: the program needs agent input. Stdout contains one JSON object with either
  `"status": "need-input"` or `"status": "rejected"`.
- Stdout is reserved for that single protocol JSON object. Diagnostics, progress, child process
  output, and logs go to stderr or `.agents/logs/`.

Answers are written as a JSON object mapping question id to answer string:

```json
{
  "build-tool": "mill"
}
```

The default path is `.agents/answers.json`. `--answers '<json>'` is accepted as a convenience and
is persisted to that same log by the runtime.

## Messages

### Need Input

```json
{
  "status": "need-input",
  "context": "Create a small Scala project.",
  "questions": [
    {
      "id": "build-tool",
      "text": "Which build tool should this project use?",
      "kind": "choice",
      "allowed": ["mill", "scala-cli", "sbt"],
      "default": "mill",
      "current": null,
      "context": null
    }
  ]
}
```

### Rejected

```json
{
  "status": "rejected",
  "problems": [
    {
      "questionId": "build-tool",
      "message": "'make' not in [mill, scala-cli, sbt]"
    }
  ],
  "questions": [
    {
      "id": "build-tool",
      "text": "Which build tool should this project use?",
      "kind": "choice",
      "allowed": ["mill", "scala-cli", "sbt"],
      "default": "mill",
      "current": null,
      "context": "'make' not in [mill, scala-cli, sbt]"
    }
  ]
}
```

### Done

```json
{
  "status": "done",
  "result": {
    "targetDir": "/tmp/new-project"
  }
}
```

## Compatibility Policy

The wire protocol version is `0.x` until the first stable release. During `0.x`, incompatible L0
changes may happen between minor versions, but every incompatible change must be called out in the
release notes and in this document.

After `1.0.0`, arrowstep follows semantic versioning for L0:

- Patch releases may add clarifying documentation or fix invalid output, but must not change valid
  message shapes.
- Minor releases may add optional fields, new question kinds, or new files under `.agents/`.
  Existing agents must be able to ignore the additions and continue operating.
- Major releases are required for removing fields, renaming fields, changing exit-code meanings,
  changing stdout discipline, or changing the answer-log format incompatibly.

Consumers should ignore unknown JSON object fields. Producers should not emit a new `"status"`
value without a major version unless the value is explicitly documented as optional extension data.

## Stable Invariants

- One protocol JSON object on stdout per process run.
- Exit `0` means complete; exit `2` means input is required.
- `answers.json` is an object of string keys to string values.
- Question ids are stable within a dialogue.
- `ValidAnswers` can only be produced through validation, so world-changing steps consume checked
  answers rather than raw agent output.
