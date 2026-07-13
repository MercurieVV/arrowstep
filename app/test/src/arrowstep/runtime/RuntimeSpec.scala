package arrowstep.runtime

import arrowstep.core.AskInput
import cats.effect.IO

final class RuntimeSpec extends munit.CatsEffectSuite:

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
