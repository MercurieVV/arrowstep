package arrowstep.runtime

import arrowstep.core.{Answers, Ask, AskInput}
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
