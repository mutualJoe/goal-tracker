package goaltracker.frontend.sync

import goaltracker.model.*
import goaltracker.model.JsonCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object SyncClient:

  final case class SyncError(message: String)

  private def request(
      method: String,
      url: String,
      body: Option[String],
      token: Option[String]
  ): Future[Either[SyncError, String]] =
    val xhr = new dom.XMLHttpRequest()
    val promise = Promise[Either[SyncError, String]]()
    xhr.open(method, url)
    xhr.setRequestHeader("Content-Type", "application/json")
    token.foreach(t => xhr.setRequestHeader("Authorization", s"Bearer $t"))
    xhr.onload = { (_: dom.Event) =>
      if xhr.status >= 200 && xhr.status < 300 then promise.success(Right(xhr.responseText))
      else
        val msg =
          decode[Map[String, String]](xhr.responseText).toOption
            .flatMap(_.get("error"))
            .getOrElse(s"HTTP ${xhr.status}")
        promise.success(Left(SyncError(msg)))
    }
    xhr.onerror = { (_: dom.Event) =>
      promise.success(Left(SyncError("Network error — is the sync server running?")))
    }
    body match
      case Some(b) => xhr.send(b)
      case None    => xhr.send()
    promise.future

  def register(baseUrl: String, username: String, password: String): Future[Either[SyncError, AuthResponse]] =
    val payload = AuthRequest(username, password).asJson.noSpaces
    request("POST", s"${baseUrl.stripSuffix("/")}/auth/register", Some(payload), None).map {
      case Left(err)  => Left(err)
      case Right(raw) => decode[AuthResponse](raw).left.map(e => SyncError(e.getMessage))
    }

  def login(baseUrl: String, username: String, password: String): Future[Either[SyncError, AuthResponse]] =
    val payload = AuthRequest(username, password).asJson.noSpaces
    request("POST", s"${baseUrl.stripSuffix("/")}/auth/login", Some(payload), None).map {
      case Left(err)  => Left(err)
      case Right(raw) => decode[AuthResponse](raw).left.map(e => SyncError(e.getMessage))
    }

  def sync(
      baseUrl: String,
      token: String,
      document: SyncDocument
  ): Future[Either[SyncError, SyncResponse]] =
    val payload = SyncRequest(document).asJson.noSpaces
    request("POST", s"${baseUrl.stripSuffix("/")}/sync", Some(payload), Some(token)).map {
      case Left(err)  => Left(err)
      case Right(raw) => decode[SyncResponse](raw).left.map(e => SyncError(e.getMessage))
    }
