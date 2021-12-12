package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.javadsl.model
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}

// 2
object DirectivesBreakdown extends App {
  implicit val system: ActorSystem = ActorSystem("DirectivesBreakdown")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  // The type 'Route' is an alias for 'RequestContext => Future[RouteResult]'
  /*
    A RequestContext contains:
        - the HttpRequest being handled
        - the actor system
        - the actor materializer
        - the logging adapter
        - routing settings
        - ...etc
       => This is the data structure handled by a Route.
       When a HTTP request comes in, it will be wrapped in this RequestContext along side the Actor system, materializer ...etc
       and all this will be handled by the Route.
       You'll most never need to build a RequestContext by yourself, because Akka HTTP will build one for you.

       Directives allows:
        - filtering and nesting
        - chaining with ~
        - extracting data

       What a Route can do with RequestContext:
        - complete it synchronously with a response
        - complete it asynchronously with a Future(response)
        - complete it synchronously by returning a Source(advanced)
        - reject it and pass it to the next Route
        - fail it
   */

  // 1- Filtering directives
  val simpleHttpMethodRoute =
    post { // equivalent directives: get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, """
          |<html>
          |  <body>
          |     Hello from Akka HTTP!
          |  </body>
          |</html>
          |""".stripMargin))
    }

  val complexPathRoute =
    path("api" / "myEndpoint") { // == /api/myEndpoint
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndpoint") { // This is a url encoded
      complete(StatusCodes.OK)
    }

  // 'path("api" / "myEndpoint")' and 'path("api/myEndpoint")' are not the same !
//  Http().bindAndHandle(complexPathRoute, "localhost", 8000)
//  Http().bindAndHandle(dontConfuse, "localhost", 8011)

  /*
  curl -XGET http://localhost:8000/api/myEndpoint ==> 200
  curl -XGET http://localhost:8011/api/myEndpoint ==> 404 .. In this case, "api/myEndpoint" is an encoded url, so we need
      to transform the "/" character to "%2F":  curl -XGET http://localhost:8011/api%2FmyEndpoint
   */

  val pathEndRoute =
    pathEndOrSingleSlash { // localhost:port OR localhost:port/
      complete(StatusCodes.OK)
    }

  // 2- Extraction directives:

  // GET on /api/item/42
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (itemNumber: Int) =>
      println(s"There is a number in the path: '$itemNumber'")
      complete(StatusCodes.OK)
    }

//  Http().bindAndHandle(pathExtractionRoute, "localhost", 8022)
  // execute: curl -XGET http://localhost:8022/api/item/12

  val pathMultiExtract =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"There are 2 numbers in the path: '$id' and '$inventory'")
      complete(StatusCodes.OK)
    }

//  Http().bindAndHandle(pathMultiExtract, "localhost", 8033)
  // execute: curl -XGET http://localhost:8033/api/order/12/1

  val queryParamExtractionRoute = //  /api/item?id=45
    path("api" / "item") {
//      parameter("id".as[Int]) { (itemId: Int) => // or we can use symbols: '("id".as[Int])' ==> ('id.as[Int])
      // The symbol "'id" will be encoded in the JVM and held inside a special memory zone, and offers performance benefits
      // because it's compared by reference equality, instead of content equality
      parameter('id.as[Int]) { (itemId: Int) =>
        println(s"Extraction of id: '$itemId'")
        complete(StatusCodes.OK)
      }
    }
//  Http().bindAndHandle(queryParamExtractionRoute, "localhost", 8055)
  // execute: curl -XGET http://localhost:8055/api/item?id=45

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { (httpRequest: HttpRequest) =>
        // Full control in the 'httpRequest'
        extractLog { (log: LoggingAdapter) =>
          log.info(s"Receiving a HTTP Request: '$httpRequest'")
          complete(StatusCodes.OK)
        }
      }
    }

}
