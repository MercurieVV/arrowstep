# arrowstep

Typed, compiler-checked, replayable dialogues between Scala programs and coding agents.

arrowstep lets a program ask structured questions, validate answers, and replay the same dialogue
across repeated CLI runs. The dialogue is a typed `Flow[F, A, B]`; the runtime decides whether an
outer agent drives the loop with `.agents/answers.json`, or the program starts its own agent session.

## Install

After the first Maven Central release:

```scala
libraryDependencies += "io.github.mercurievv" %% "arrowstep" % "<version>"
```

For local development from this repository:

```scala
//> using file app/src
//> using dep org.typelevel::cats-core:2.13.0
//> using dep org.typelevel::cats-effect:3.7.0
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep com.lihaoyi::ujson:4.4.3
```

## Quickstart

Define questions, plug in an `Ask[F]`, validate the answers, and compose the rest of the program as
a typed flow.

```scala
import arrowstep.core.*
import arrowstep.runtime.StubAsk
import cats.Id

val input =
  AskInput(
    questions = List(
      Question(
        id = "build-tool",
        text = "Which build tool should this project use?",
        kind = QuestionKind.Choice(List("mill", "scala-cli", "sbt")),
        default = Some("mill"),
        current = None,
        context = None
      )
    ),
    context = Some("Create a small Scala project.")
  )

val ask = StubAsk[Id](Answers(Map("build-tool" -> "mill")))
val flow = Dialogue.askUntilValid[Id](ask, Validator.basic[Id])

val answers = flow.run(input)
answers.toMap("build-tool")
```

For an agent-led CLI, run the consumer with `--agent`. If the program exits `2`, stdout contains
questions as JSON. Write answers to `.agents/answers.json` or pass `--answers '<json>'`, then rerun
the same command until it exits `0`.

```bash
scala-cli run Setup.scala -- --agent /tmp/new-project
scala-cli run Setup.scala -- --agent /tmp/new-project --answers '{"build-tool":"mill"}'
```

Runtime stdout is protocol JSON only. Human-readable progress belongs on stderr.

## Docs

- [Design](docs/DESIGN.md)
- [Decisions](docs/DECISIONS.md)
- [Wire protocol](docs/WIRE_PROTOCOL.md)
- [Release](docs/RELEASE.md)
- [Roadmap](docs/ROADMAP.md)

Run the checks:

```bash
mill app.test
mill docs.run
```
