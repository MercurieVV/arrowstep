package arrowstep.runtime

import arrowstep.core.{Answers, Ask, AskInput, ProgramSays, Question}
import cats.effect.Sync
import cats.syntax.all.*

import scala.util.Try

final case class SessionId(value: String)
final case class AgentPurpose(value: String)

final case class AgentAdapter(name: String, fresh: List[String], resume: List[String])

object AgentAdapter:

  val Claude: AgentAdapter =
    AgentAdapter(
      name = "claude",
      fresh = List("claude", "-p", "--output-format", "json", "{prompt}"),
      resume = List("claude", "-p", "--resume", "{session}", "--output-format", "json", "{prompt}")
    )

  val Gemini: AgentAdapter =
    AgentAdapter(
      name = "gemini",
      fresh = List("gemini", "-p", "--output-format", "json", "{prompt}"),
      resume = List("gemini", "-p", "--resume", "{session}", "--output-format", "json", "{prompt}")
    )

final case class AdapterRegistry(adapters: Map[String, AgentAdapter]):
  def get(name: String): Option[AgentAdapter] =
    adapters.get(name)

  def updated(adapter: AgentAdapter): AdapterRegistry =
    copy(adapters = adapters.updated(adapter.name, adapter))

object AdapterRegistry:

  private val AgentsDir = ".agents"
  private val AdaptersFile = "adapters.json"

  val default: AdapterRegistry =
    AdapterRegistry(List(AgentAdapter.Claude, AgentAdapter.Gemini).map(a => a.name -> a).toMap)

  def load[F[_]: Sync]: F[AdapterRegistry] =
    Sync[F].delay(os.pwd).flatMap(load[F])

  def load[F[_]: Sync](root: os.Path): F[AdapterRegistry] =
    Sync[F].delay {
      val file = root / AgentsDir / AdaptersFile
      val configured =
        if os.exists(file) then parse(os.read(file)).getOrElse(AdapterRegistry(Map.empty))
        else AdapterRegistry(Map.empty)

      configured.adapters.values.foldLeft(default)(_.updated(_))
    }

  private def parse(raw: String): Option[AdapterRegistry] =
    Try(ujson.read(raw)).toOption.flatMap {
      case ujson.Obj(values) =>
        values.toList
          .traverse { case (name, value) => parseAdapter(name, value).map(adapter => name -> adapter) }
          .map(entries => AdapterRegistry(entries.toMap))
      case _ => None
    }

  private def parseAdapter(name: String, value: ujson.Value): Option[AgentAdapter] =
    value match
      case ujson.Obj(fields) =>
        for
          fresh <- fields.get("new").flatMap(parseCommand)
          resume <- fields.get("resume").flatMap(parseCommand)
        yield AgentAdapter(name, fresh, resume)
      case _ => None

  private def parseCommand(value: ujson.Value): Option[List[String]] =
    value match
      case ujson.Arr(items) =>
        items.toList.traverse {
          case ujson.Str(part) => Some(part)
          case _               => None
        }
      case _ => None

object SessionStore:

  private val AgentsDir = ".agents"
  private val SessionsFile = "sessions.json"

  def read[F[_]: Sync]: F[Map[AgentPurpose, SessionId]] =
    Sync[F].delay(os.pwd).flatMap(read[F])

  def read[F[_]: Sync](root: os.Path): F[Map[AgentPurpose, SessionId]] =
    Sync[F].delay {
      val file = path(root)
      if os.exists(file) then parse(os.read(file)).getOrElse(Map.empty)
      else Map.empty
    }

  def get[F[_]: Sync](root: os.Path, purpose: AgentPurpose): F[Option[SessionId]] =
    read[F](root).map(_.get(purpose))

  def put[F[_]: Sync](root: os.Path, purpose: AgentPurpose, session: SessionId): F[Unit] =
    read[F](root).flatMap(sessions => write[F](root, sessions.updated(purpose, session)))

  def write[F[_]: Sync](root: os.Path, sessions: Map[AgentPurpose, SessionId]): F[Unit] =
    Sync[F].delay {
      os.write.over(path(root), render(sessions), createFolders = true)
    }

  private def path(root: os.Path): os.Path =
    root / AgentsDir / SessionsFile

  private def parse(raw: String): Option[Map[AgentPurpose, SessionId]] =
    Try(ujson.read(raw)).toOption.flatMap {
      case ujson.Obj(values) =>
        values.toList
          .traverse {
            case (purpose, ujson.Str(session)) => Some(AgentPurpose(purpose) -> SessionId(session))
            case _                            => None
          }
          .map(_.toMap)
      case _ => None
    }

  private def render(sessions: Map[AgentPurpose, SessionId]): String =
    ujson.Obj.from(
      sessions.toList
        .sortBy { case (purpose, _) => purpose.value }
        .map { case (purpose, session) => purpose.value -> ujson.Str(session.value) }
    ).render()

final case class LiveAskConfig(
    adapter: AgentAdapter,
    purpose: AgentPurpose,
    root: os.Path
)

final case class ReplayNeedInput(input: AskInput)
    extends RuntimeException("Replay answer log is missing answers for the requested questions"):
  def programSays: ProgramSays[Nothing] =
    ProgramSays.NeedInput(input.context, input.questions)

final class ReplayAsk[F[_]: Sync](root: os.Path) extends Ask[F]:

  def apply(input: AskInput): F[Answers] =
    AnswerLog.read[F](root).flatMap { log =>
      val missing = ReplayAsk.missing(input.questions, log)
      if missing.isEmpty then Sync[F].pure(log)
      else Sync[F].raiseError(ReplayNeedInput(input.copy(questions = missing)))
    }

object ReplayAsk:

  def apply[F[_]: Sync]: F[ReplayAsk[F]] =
    Sync[F].delay(os.pwd).map(apply[F])

  def apply[F[_]: Sync](root: os.Path): ReplayAsk[F] =
    new ReplayAsk[F](root)

  private def missing(questions: List[Question], answers: Answers): List[Question] =
    questions.filter(q => answers.get(q.id).isEmpty)

final class LiveAsk[F[_]: Sync](config: LiveAskConfig) extends Ask[F]:

  def apply(input: AskInput): F[Answers] =
    Sync[F].raiseError(
      new UnsupportedOperationException(
        "LiveAsk process execution is planned for Phase 3; Phase 0 only defines its runtime boundary."
      )
    )

object LiveAsk:
  def apply[F[_]: Sync](config: LiveAskConfig): LiveAsk[F] =
    new LiveAsk[F](config)

object Runtime:
  val defaultAdapters: AdapterRegistry =
    AdapterRegistry.default

final case class AgentArgs(
    agent: Boolean,
    inlineAnswers: Option[Answers],
    fresh: Boolean,
    resumeSession: Option[SessionId],
    adapter: Option[String]
)

object AgentArgs:

  val empty: AgentArgs =
    AgentArgs(agent = false, inlineAnswers = None, fresh = false, resumeSession = None, adapter = None)

  def parse(args: List[String]): Either[String, AgentArgs] =
    parseLoop(args, empty)

  private def parseLoop(args: List[String], parsed: AgentArgs): Either[String, AgentArgs] =
    args match
      case Nil => Right(parsed)
      case "--agent" :: tail =>
        parseLoop(tail, parsed.copy(agent = true))
      case "--fresh" :: tail =>
        parseLoop(tail, parsed.copy(fresh = true))
      case "--answers" :: raw :: tail =>
        AnswerLog.parse(raw) match
          case Some(answers) => parseLoop(tail, parsed.copy(inlineAnswers = Some(answers)))
          case None          => Left("invalid JSON for --answers")
      case "--answers" :: Nil =>
        Left("missing value for --answers")
      case "--resume-session" :: value :: tail =>
        parseLoop(tail, parsed.copy(resumeSession = Some(SessionId(value))))
      case "--resume-session" :: Nil =>
        Left("missing value for --resume-session")
      case "--adapter" :: value :: tail =>
        parseLoop(tail, parsed.copy(adapter = Some(value)))
      case "--adapter" :: Nil =>
        Left("missing value for --adapter")
      case other :: _ =>
        Left("unknown argument: " + other)

object AgentMain:

  final case class Outcome(stdout: String, stderr: String, exitCode: Int)

  def run[F[_]: Sync](
      args: List[String],
      root: os.Path
  )(program: AgentArgs => F[ProgramSays[ujson.Value]]): F[Outcome] =
    AgentArgs.parse(args) match
      case Left(message) =>
        Sync[F].pure(Outcome("", message, 2))
      case Right(parsed) =>
        persistInlineAnswers(root, parsed) *> run(program(parsed))

  def run[F[_]: Sync](program: F[ProgramSays[ujson.Value]]): F[Outcome] =
    program
      .map(render)
      .recover { case need: ReplayNeedInput =>
        render(need.programSays)
      }

  def render(programSays: ProgramSays[ujson.Value]): Outcome =
    Outcome(
      stdout = ProtocolJson.render(programSays),
      stderr = "",
      exitCode = programSays.exitCode
    )

  private def persistInlineAnswers[F[_]: Sync](root: os.Path, args: AgentArgs): F[Unit] =
    args.inlineAnswers.fold(Sync[F].unit) { inline =>
      for
        existing <- AnswerLog.read[F](root)
        _ <- AnswerLog.write[F](root, AnswerLog.merge(existing, inline))
      yield ()
    }
