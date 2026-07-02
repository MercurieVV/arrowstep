package arrowstep.runtime

import arrowstep.core.Answers
import cats.effect.Sync
import cats.syntax.all.*

import scala.util.Try

object AnswerLog:

  private val AgentsDir = ".agents"
  private val AnswersFile = "answers.json"

  def read[F[_]: Sync]: F[Answers] =
    Sync[F].delay(os.pwd).flatMap(read[F])

  def read[F[_]: Sync](root: os.Path): F[Answers] =
    Sync[F].delay {
      val file = path(root)
      if os.exists(file) then parse(os.read(file)).getOrElse(Answers(Map.empty))
      else Answers(Map.empty)
    }

  def write[F[_]: Sync](a: Answers): F[Unit] =
    Sync[F].delay(os.pwd).flatMap(write[F](_, a))

  def write[F[_]: Sync](root: os.Path, a: Answers): F[Unit] =
    Sync[F].delay {
      val dir = root / AgentsDir
      os.makeDir.all(dir)
      os.write.over(path(root), render(a), createFolders = true)
    }

  def merge(left: Answers, right: Answers): Answers =
    Answers(left.toMap ++ right.toMap)

  def upsert(answers: Answers, id: String, answer: String): Answers =
    Answers(answers.toMap.updated(id, answer))

  private def path(root: os.Path): os.Path =
    root / AgentsDir / AnswersFile

  private def parse(raw: String): Option[Answers] =
    Try(ujson.read(raw)).toOption.flatMap {
      case ujson.Obj(values) =>
        values.toList
          .traverse {
            case (id, ujson.Str(answer)) => Some(id -> answer)
            case _                       => None
          }
          .map(entries => Answers(entries.toMap))
      case _ => None
    }

  private def render(a: Answers): String =
    ujson.Obj.from(a.toMap.toList.sortBy(_._1).map { case (id, answer) =>
      id -> ujson.Str(answer)
    }).render()
