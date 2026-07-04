package arrowstep.runtime

import arrowstep.core.Problem
import arrowstep.core.ProgramSays
import arrowstep.core.Question
import arrowstep.core.QuestionKind

final class ProtocolJsonSpec extends munit.FunSuite:

  test("renders NeedInput to the L0 wire shape") {
    val question = Question(
      id = "web-server",
      text = "Enable Web Server?",
      kind = QuestionKind.Choice(List("yes", "no")),
      default = Some("no"),
      current = Some("no"),
      context = None
    )

    val rendered = ProtocolJson.render(
      ProgramSays.NeedInput(Some("Configuring Scala project ./x"), List(question))
    )

    assertEquals(
      rendered,
      """{"status":"need-input","context":"Configuring Scala project ./x","questions":[{"id":"web-server","text":"Enable Web Server?","kind":"choice","allowed":["yes","no"],"default":"no","current":"no","context":null}]}"""
    )
  }

  test("renders Rejected with problems and re-stated questions") {
    val question = Question(
      id = "module-name",
      text = "Module name?",
      kind = QuestionKind.FreeText,
      default = None,
      current = Some(""),
      context = Some("Use a Scala identifier.")
    )

    val rendered = ProtocolJson.toJson(
      ProgramSays.Rejected(
        List(Problem("module-name", "must not be empty")),
        List(question)
      )
    )

    assertEquals(rendered("status").str, "rejected")
    assertEquals(rendered("problems")(0)("questionId").str, "module-name")
    assertEquals(rendered("problems")(0)("message").str, "must not be empty")
    assertEquals(rendered("questions")(0)("kind").str, "free-text")
    assert(!rendered("questions")(0).obj.contains("allowed"))
    assertEquals(rendered("questions")(0)("default"), ujson.Null)
    assertEquals(rendered("questions")(0)("current").str, "")
    assertEquals(rendered("questions")(0)("context").str, "Use a Scala identifier.")
  }

  test("renders Done with consumer JSON passed through") {
    val result = ujson.Obj("ok" -> true, "path" -> "build.sbt")

    assertEquals(
      ProtocolJson.render(ProgramSays.Done(result)),
      """{"status":"done","result":{"ok":true,"path":"build.sbt"}}"""
    )
  }
