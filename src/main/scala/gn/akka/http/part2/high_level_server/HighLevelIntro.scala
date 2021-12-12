package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import gn.akka.http.part1.low_level_server.HttpsContext

// 1
object HighLevelIntro extends App {
  implicit val system: ActorSystem = ActorSystem("HighLevelIntro")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route = path("home") {
    complete(StatusCodes.OK)
  }
  // 'path' and 'complete' are directives
  // Directives are high-level building blocs from Akka HTTP sever logic
  // The 'path("home")' directive will filter the requests that try to hit the '/home' path, and if the HTTP request passes
  // this filter, then the other directive 'complete(StatusCodes.OK)' decide what will happen, which will send a HTTP
  // response with the status code OK/200

  val pathGetRoute: Route = {
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }

  Http().bindAndHandle(simpleRoute, "localhost", 8050)
// Hit curl -XGET http://localhost:8050/home or curl -XPOST http://localhost:8050/home ===> Result: OK

  Http().bindAndHandle(pathGetRoute, "localhost", 8060)
// Handles only to Hit curl -XGET http://localhost:8060/home
// If try to trigger a POST request, the server will respond 'HTTP method not allowed, supported methods: GET', and this
// logic is set by the directive 'get' in 'pathGetRoute'

  // chaining directives with ~
  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ // == otherwise
        post {
          complete(StatusCodes.Forbidden)
        }
      // If the http request is a 'get', the server will return 'OK'.
      // If the http request is a 'post', the server will return 'Forbidden'.
    } ~
      path("home") {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, """
            |<html>
            |  <body>
            |     Hello from Akka HTTP!
            |  </body>
            |</html>
            |""".stripMargin))
      }
  // The 'chainedRoute' is a routing tree
  // You can pass the https configuration as well:
  Http().bindAndHandle(chainedRoute, "localhost", 8070, HttpsContext.httpsConnectionContext)
  /*
  Go to browser:
    curl -XGET https://localhost:8070/myEndpoint
    curl -XPOST https://localhost:8070/myEndpoint

    curl -XGET https://localhost:8070/home
    curl -XPOST https://localhost:8070/home
   */

}
