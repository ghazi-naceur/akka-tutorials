package gn.akka.http.part1.low_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
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

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 1
object LowLevelAPI extends App {
  implicit val system: ActorSystem = ActorSystem("LowLevelServerAPI")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from: '${connection.remoteAddress}'")
  }
  private val serverBindingFuture: Future[Http.ServerBinding] = serverSource.to(connectionSink).run()

  serverBindingFuture.onComplete {
    case Success(binding) =>
      // you can programmatically terminate the server:
//      binding.unbind() // it will stop the listener on localhost:8000
//      binding.terminate(30 seconds) // this method is more aggressive
      println(s"Server binding successful: '$binding'")
    case Failure(exception) => println(s"Server binding failed: $exception")
  }
  /*
  Run the app above and execute this command on the terminal: `curl localhost:8000`, and monitor the console
  or the browser
   */

  // Method 1: synchronously (or in the same thread) serve HTTP responses
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
            |<html>
            |  <body>
            |     Hello from Akka HTTP!
            |  </body>
            |</html>
            |""".stripMargin)
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
            |<html>
            |  <body>
            |     Resource can't be found.
            |  </body>
            |</html>
            |""".stripMargin)
      )
  }

  val httpSinkConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  // 'Http().bind("localhost", 8010)' a source of 'IncomingConnection'
  // 'httpSinkConnectionHandler' a sink of 'IncomingConnection'
//    Http().bind("localhost", 8010).runWith(httpSinkConnectionHandler) // Akka stream example
//  We can rewrite the statement above as follows:
  Http().bindAndHandleSync(requestHandler, "localhost", 8010)

  // Method 2: Serve HTTP responses asynchronously (using Future = on some other thread)
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(
        HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                                 |<html>
                                                                 |  <body>
                                                                 |     Hello from Akka HTTP!
                                                                 |  </body>
                                                                 |</html>
                                                                 |""".stripMargin)
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(
        HttpResponse(
          StatusCodes.NotFound, // HTTP 404
          entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                               |<html>
                                                               |  <body>
                                                               |     Resource can't be found.
                                                               |  </body>
                                                               |</html>
                                                               |""".stripMargin)
        )
      )
  }

  val asyncHttpSinkConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

//  Http().bind("localhost", 8020).runWith(asyncHttpSinkConnectionHandler)
  /*
    Run the app above and execute this command on the terminal: `curl localhost:8020/home`, and monitor the console
    or the browser
   */
  // Short version of the statement above:
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8020)

  // Method 3: Async via Akka Streams
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                                 |<html>
                                                                 |  <body>
                                                                 |     Hello from Akka HTTP!
                                                                 |  </body>
                                                                 |</html>
                                                                 |""".stripMargin)
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                                 |<html>
                                                                 |  <body>
                                                                 |     Resource can't be found.
                                                                 |  </body>
                                                                 |</html>
                                                                 |""".stripMargin)
      )
  }

//  Http().bind("localhost", 8030).runForeach { connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }
  // short hand version:
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8030)

}
