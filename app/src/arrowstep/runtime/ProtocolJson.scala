package arrowstep.runtime

import arrowstep.core.Problem
import arrowstep.core.ProgramSays
import arrowstep.core.Question
import arrowstep.core.QuestionKind

object ProtocolJson:

  def render(programSays: ProgramSays[ujson.Value]): String =
    toJson(programSays).render()

  def toJson(programSays: ProgramSays[ujson.Value]): ujson.Value =
    programSays match
      case ProgramSays.NeedInput(context, questions) =>
        ujson.Obj.from(
          Seq(
            "status" -> ujson.Str("need-input"),
            "context" -> optionString(context),
            "questions" -> questionsJson(questions)
          )
        )

      case ProgramSays.Rejected(problems, questions) =>
        ujson.Obj.from(
          Seq(
            "status" -> ujson.Str("rejected"),
            "problems" -> ujson.Arr.from(problems.map(problemJson)),
            "questions" -> questionsJson(questions)
          )
        )

      case ProgramSays.Done(result) =>
        ujson.Obj.from(
          Seq(
            "status" -> ujson.Str("done"),
            "result" -> result
          )
        )

  private def questionsJson(questions: List[Question]): ujson.Value =
    ujson.Arr.from(questions.map(questionJson))

  private def questionJson(question: Question): ujson.Value =
    ujson.Obj.from(questionFields(question))

  private def questionFields(question: Question): Seq[(String, ujson.Value)] =
    Seq(
      "id" -> ujson.Str(question.id),
      "text" -> ujson.Str(question.text)
    ) ++ kindFields(question.kind) ++ Seq(
      "default" -> optionString(question.default),
      "current" -> optionString(question.current),
      "context" -> optionString(question.context)
    )

  private def kindFields(kind: QuestionKind): Seq[(String, ujson.Value)] =
    kind match
      case QuestionKind.FreeText =>
        Seq("kind" -> ujson.Str("free-text"))
      case QuestionKind.Choice(allowed) =>
        Seq(
          "kind" -> ujson.Str("choice"),
          "allowed" -> ujson.Arr.from(allowed.map(ujson.Str(_)))
        )

  private def problemJson(problem: Problem): ujson.Value =
    ujson.Obj.from(
      Seq(
        "questionId" -> ujson.Str(problem.questionId),
        "message" -> ujson.Str(problem.message)
      )
    )

  private def optionString(value: Option[String]): ujson.Value =
    value.fold[ujson.Value](ujson.Null)(ujson.Str(_))
