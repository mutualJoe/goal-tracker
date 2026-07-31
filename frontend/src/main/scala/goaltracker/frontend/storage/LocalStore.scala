package goaltracker.frontend.storage

import goaltracker.model.*
import goaltracker.model.JsonCodecs.given
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalajs.dom

/** Offline-first persistence via localStorage.
  *
  * Document shape matches what an IndexedDB store would hold; swap `read` /
  * `write` internals later without touching UI or merge logic.
  */
object LocalStore:
  private val DocKey = "goal-tracker:document"
  private val DeviceKey = "goal-tracker:device-id"
  private val AuthKey = "goal-tracker:auth"
  private val ServerKey = "goal-tracker:server-url"

  final case class AuthSession(token: String, userId: String, username: String)
  private given Encoder[AuthSession] = deriveEncoder
  private given Decoder[AuthSession] = deriveDecoder

  def deviceId(): DeviceId =
    Option(dom.window.localStorage.getItem(DeviceKey)) match
      case Some(id) if id.nonEmpty => DeviceId(id)
      case _ =>
        val id = DeviceId(Ids.newId())
        dom.window.localStorage.setItem(DeviceKey, id.value)
        id

  def loadDocument(): SyncDocument =
    Option(dom.window.localStorage.getItem(DocKey))
      .flatMap(raw => decode[SyncDocument](raw).toOption)
      .getOrElse(SyncDocument.empty)

  def saveDocument(doc: SyncDocument): Unit =
    dom.window.localStorage.setItem(DocKey, doc.asJson.noSpaces)

  def loadAuth(): Option[AuthSession] =
    Option(dom.window.localStorage.getItem(AuthKey)).flatMap(raw => decode[AuthSession](raw).toOption)

  def saveAuth(session: AuthSession): Unit =
    dom.window.localStorage.setItem(AuthKey, session.asJson.noSpaces)

  def clearAuth(): Unit =
    dom.window.localStorage.removeItem(AuthKey)

  def serverUrl(): String =
    Option(dom.window.localStorage.getItem(ServerKey)).getOrElse("http://localhost:8080")

  def saveServerUrl(url: String): Unit =
    dom.window.localStorage.setItem(ServerKey, url.trim)
