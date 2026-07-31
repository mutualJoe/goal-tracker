package goaltracker.backend

import cats.effect.IO
import cats.syntax.all.*
import goaltracker.model.*
import goaltracker.model.JsonCodecs.given
import goaltracker.validation.Validation
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.server.staticcontent.*
import fs2.io.file.Path as Fs2Path

final class Routes(store: Store, webRoot: java.nio.file.Path):

  private def bearer(req: Request[IO]): Option[String] =
    req.headers.get[Authorization].collect {
      case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => token
    }

  private def errorJson(msg: String): IO[Response[IO]] =
    BadRequest(Map("error" -> msg).asJson)

  private def unauthorizedJson(msg: String): IO[Response[IO]] =
    IO.pure(Response[IO](Status.Unauthorized).withEntity(Map("error" -> msg).asJson))

  val httpApp: HttpApp[IO] =
    val api = HttpRoutes.of[IO] {
      case req @ POST -> Root / "auth" / "register" =>
        req.as[AuthRequest].flatMap { body =>
          Validation.auth(body.username, body.password).toEither match
            case Left(errs) => errorJson(errs.map(_.message).mkString("; "))
            case Right((u, p)) =>
              store.register(u, p) match
                case Left(err)  => Conflict(Map("error" -> err).asJson)
                case Right(res) => Created(res.asJson)
        }

      case req @ POST -> Root / "auth" / "login" =>
        req.as[AuthRequest].flatMap { body =>
          store.login(body.username, body.password) match
            case Left(err)  => unauthorizedJson(err)
            case Right(res) => Ok(res.asJson)
        }

      case req @ POST -> Root / "sync" =>
        bearer(req) match
          case None => unauthorizedJson("Missing bearer token")
          case Some(token) =>
            store.session(token) match
              case None => unauthorizedJson("Invalid token")
              case Some((userId, _)) =>
                req.as[SyncRequest].flatMap { body =>
                  Ok(store.sync(userId, body.document).asJson)
                }

      case GET -> Root / "health" =>
        Ok(Map("status" -> "ok", "users" -> store.userCount.toString).asJson)
    }

    val staticFiles = fileService[IO](FileService.Config[IO](webRoot.toAbsolutePath.toString))

    val index = HttpRoutes.of[IO] {
      case GET -> Root =>
        StaticFile
          .fromPath[IO](Fs2Path.fromNioPath(webRoot.resolve("index.html")))
          .getOrElseF(NotFound())
    }

    (api <+> index <+> staticFiles).orNotFound
