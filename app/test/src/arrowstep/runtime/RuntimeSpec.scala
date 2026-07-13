package arrowstep.runtime

import arrowstep.core.{Answers, AskInput, Dialogue, ProgramSays, Question, QuestionKind, Validator}
import cats.effect.IO

final class RuntimeSpec extends munit.CatsEffectSuite:

  private val question =
    Question("lang", "Language?", QuestionKind.Choice(List("scala", "java")), None, None, None)

  private val input =
    AskInput(List(question), Some("Project setup"))

  test("SessionStore persists purpose-scoped session ids under .agents/sessions.json") {
    withTempDir { root =>
      val setup =
        for
          _ <- SessionStore.put[IO](root, AgentPurpose("setup"), SessionId("session-1"))
          _ <- SessionStore.put[IO](root, AgentPurpose("review"), SessionId("session-2"))
          sessions <- SessionStore.read[IO](root)
          file <- IO(os.read(root / ".agents" / "sessions.json"))
        yield
          assertEquals(sessions.get(AgentPurpose("setup")), Some(SessionId("session-1")))
          assertEquals(sessions.get(AgentPurpose("review")), Some(SessionId("session-2")))
          assertEquals(file, """{"review":"session-2","setup":"session-1"}""")

      setup
    }
  }

  test("SessionStore treats absent and malformed files as empty sessions") {
    withTempDir { root =>
      for
        absent <- SessionStore.read[IO](root)
        _ <- IO(os.write(root / ".agents" / "sessions.json", "{", createFolders = true))
        malformed <- SessionStore.read[IO](root)
      yield
        assertEquals(absent, Map.empty[AgentPurpose, SessionId])
        assertEquals(malformed, Map.empty[AgentPurpose, SessionId])
    }
  }

  test("AdapterRegistry ships claude and gemini defaults") {
    val registry = AdapterRegistry.default

    assertEquals(registry.get("claude").map(_.fresh.headOption), Some(Some("claude")))
    assertEquals(registry.get("gemini").map(_.resume.headOption), Some(Some("gemini")))
  }

  test("AdapterRegistry overlays .agents/adapters.json on top of defaults") {
    withTempDir { root =>
      val configured =
        """{"claude":{"new":["custom-claude","{prompt}"],"resume":["custom-claude","--resume","{session}","{prompt}"]},"aider":{"new":["aider","--message","{prompt}"],"resume":["aider","--message","{prompt}"]}}"""

      for
        _ <- IO(os.write(root / ".agents" / "adapters.json", configured, createFolders = true))
        registry <- AdapterRegistry.load[IO](root)
      yield
        assertEquals(registry.get("claude").map(_.fresh), Some(List("custom-claude", "{prompt}")))
        assertEquals(registry.get("gemini").map(_.fresh.headOption), Some(Some("gemini")))
        assertEquals(registry.get("aider").map(_.resume), Some(List("aider", "--message", "{prompt}")))
    }
  }

  test("ReplayAsk returns the answer log when every requested question is answered") {
    withTempDir { root =>
      val answers = Answers(Map("lang" -> "scala", "extra" -> "kept"))

      for
        _ <- AnswerLog.write[IO](root, answers)
        replayed <- ReplayAsk[IO](root)(input)
      yield assertEquals(replayed.toMap, answers.toMap)
    }
  }

  test("ReplayAsk raises NeedInput with only missing questions") {
    withTempDir { root =>
      val build = Question("build", "Build tool?", QuestionKind.Choice(List("mill", "sbt")), None, None, None)
      val fullInput = input.copy(questions = List(question, build))

      for
        _ <- AnswerLog.write[IO](root, Answers(Map("lang" -> "scala")))
        result <- ReplayAsk[IO](root)(fullInput).attempt
      yield
        val says = result match
          case Left(need: ReplayNeedInput) => need.programSays
          case Left(other)                 => ProgramSays.Done(other.getMessage)
          case Right(_)                    => ProgramSays.Done("answered")

        assertEquals(says, ProgramSays.NeedInput(Some("Project setup"), List(build)))
    }
  }

  test("ReplayAsk can drive askUntilValid from a complete replay log") {
    withTempDir { root =>
      for
        _ <- AnswerLog.write[IO](root, Answers(Map("lang" -> "scala")))
        valid <- Dialogue.askUntilValid(ReplayAsk[IO](root), Validator.basic[IO]).run(input)
      yield assertEquals(valid.toMap, Map("lang" -> "scala"))
    }
  }

  test("AgentArgs parses supported Phase 1 flags") {
    val parsed = AgentArgs.parse(
      List(
        "--agent",
        "--answers",
        """{"lang":"scala"}""",
        "--fresh",
        "--resume-session",
        "session-1",
        "--adapter",
        "claude"
      )
    )

    assertEquals(
      parsed,
      Right(
        AgentArgs(
          agent = true,
          inlineAnswers = Some(Answers(Map("lang" -> "scala"))),
          fresh = true,
          resumeSession = Some(SessionId("session-1")),
          adapter = Some("claude")
        )
      )
    )
  }

  test("AgentMain persists inline --answers before running the program") {
    withTempDir { root =>
      for
        outcome <- AgentMain.run[IO](List("--answers", """{"lang":"scala"}"""), root) { _ =>
          IO.pure(ProgramSays.Done(ujson.Obj("ok" -> true)))
        }
        answers <- AnswerLog.read[IO](root)
      yield
        assertEquals(answers.toMap, Map("lang" -> "scala"))
        assertEquals(outcome.exitCode, 0)
        assertEquals(outcome.stdout, """{"status":"done","result":{"ok":true}}""")
        assertEquals(outcome.stderr, "")
    }
  }

  test("AgentMain renders ReplayNeedInput as protocol stdout and exit code 2") {
    val missing = ReplayNeedInput(input)

    AgentMain.run[IO](IO.raiseError(missing)).map { outcome =>
      assertEquals(outcome.exitCode, 2)
      assertEquals(outcome.stderr, "")
      assertEquals(
        outcome.stdout,
        """{"status":"need-input","context":"Project setup","questions":[{"id":"lang","text":"Language?","kind":"choice","allowed":["scala","java"],"default":null,"current":null,"context":null}]}"""
      )
    }
  }

  test("LiveAsk is an explicit Phase 0 boundary, not a process runner yet") {
    withTempDir { root =>
      val ask = LiveAsk[IO](LiveAskConfig(AgentAdapter.Claude, AgentPurpose("setup"), root))

      ask(AskInput(Nil, None)).attempt.map { result =>
        val message = result match
          case Left(error) => error.getMessage
          case Right(_)    => ""

        assert(message.contains("Phase 3"))
      }
    }
  }

  private def withTempDir(test: os.Path => IO[Unit]): IO[Unit] =
    IO(os.temp.dir(prefix = "arrowstep-runtime-")).bracket(test)(root => IO(os.remove.all(root)))
