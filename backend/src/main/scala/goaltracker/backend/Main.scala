package goaltracker.backend

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS
import java.nio.file.Paths

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    val webRoot = Paths.get(sys.props.getOrElse("web.root", "web")).toAbsolutePath.normalize()
    val store = new Store
    val routes = new Routes(store, webRoot)
    val app = CORS.policy.withAllowOriginAll.apply(routes.httpApp)

    IO.println(s"Grove sync server · http://localhost:8080  (web root: $webRoot)") *>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(app)
        .build
        .useForever
