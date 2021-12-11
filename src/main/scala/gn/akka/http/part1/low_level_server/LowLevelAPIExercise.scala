package gn.akka.http.part1.low_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes,
  Uri
}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.sun.net.httpserver.Headers

// 2
import scala.concurrent.Future

object LowLevelAPIExercise extends App {
  implicit val system: ActorSystem = ActorSystem("LowLevelAPIExercise")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  /**
    * Exercise: Create your own HTTP server running on localhost 8000, which replies:
    *  - with a welcome message on the 'front door' = localhost:8000
    *  - with a proper HTML on localhost:8000/about
    *  - with a redirection to some other part of the website on localhost:8000/search
    *  - with a 404 message otherwise
    */

  val syncExerciseHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        // status code OK (200) is default
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello to front door")
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        // status code OK (200) is default
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                               |<html>
                                                               |  <body>
                                                               |     About page.
                                                               |  </body>
                                                               |</html>
                                                               |""".stripMargin)
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found, // 302
        headers = List(Location("http://google.com")) // redirection to google.com
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(StatusCodes.NotFound, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "Wrong path"))
  }

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandleSync(syncExerciseHandler, "localhost", 8000)

  bindingFuture
    .flatMap(binding => binding.unbind()) // shutting down the http server
    .onComplete(_ => system.terminate()) // shutting down the Actor System
}
