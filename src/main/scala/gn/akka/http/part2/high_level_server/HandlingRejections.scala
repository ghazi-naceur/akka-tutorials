package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.javadsl.server.MissingQueryParamRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{MethodRejection, Rejection, RejectionHandler}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object HandlingRejections extends App {

  import spray.json._
  implicit val system: ActorSystem = ActorSystem("HandlingRejections")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
    }

  // If a HTTP Request matches any of these filter directives (get or path), it's going to be handled by the complete
  // directives written, but if it doesn't match the filter directives, we call that a 'rejected route'.
  // A rejected route is not a failed route.
  // A rejection is passing the request to another branch in the routing tree.
  // Rejections are aggregated, when jumping on the routing tree, without finding the requested http branch
  // If the request matches the query param filter and extraction directive, that list of previous rejections is cleared
  // for new possible rejections to be added to that rejection list
  // If there is no directive matches the request, by default Akka HTTP will return '404 Not Found', but we can choose
  // how to handle the rejection list

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) { // This will handle rejections from the top level
      // define server logic inside
      // Any rejection that's being popped by the server logic is going to be handled by 'badRequestHandler'
      // Saying that you send a POST HTTP request instead of GET, then this 'badRequestHandler' will handle that rejection
      // and complete the original request with 'BadRequest', instead of 'NotFound'(by default)

      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) { // This will handle rejections within
              parameter('myParam) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8020)

  /*
  curl -XGET http://localhost:8020/api/myEndpoint
    ==> OK

  curl -XPOST http://localhost:8020/api/myEndpoint?myParam=2
    ==> OK

  curl -XPUT http://localhost:8020/api/myEndpoint
    ==> Bad Request (The request contains bad syntax or cannot be fulfilled)
    // the 'badRequestHandler' kicked in, and in the log we'll receive all the rejected paths:
    "I have encountered rejections: List(MethodRejection(HttpMethod(GET)), MethodRejection(HttpMethod(POST)))"

  curl -XPOST http://localhost:8020/api/myEndpoint?newParam=2
    ==> Forbidden (The request was a legal request, but the server is refusing to respond to it)
    // The 'forbiddenHandler' handler kicked in
    // In the log, you will notice that the rejection list is cleared from the previous errors and it contain new one :
    "I have encountered rejections: List(MissingQueryParamRejection(myParam))"

  curl -XGET http://localhost:8020/api/otherEndpoint
    ==> Bad Request (The request contains bad syntax or cannot be fulfilled.)
    This time the rejection list is empty (no rejections added to the list):
    "I have encountered rejections: List()"
    => This is a special case for 'Not Found' response
   */

//  RejectionHandler.default // is the implicit default rejection handler (passed automatically)
  // to define a custom rejection handler:
  implicit val customRejectionHandler: RejectionHandler = // it will override the rejection handler by default
    RejectionHandler
      .newBuilder()
      .handle {
        case m: MethodRejection =>
          println(s"I got a method rejection: '$m'")
          complete("Rejected method!")
      }
      .handle {
        case m: MissingQueryParamRejection =>
          println(s"I got a query param rejection: '$m'")
          complete("Rejected query param!")
      }
      .result()

  Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8030)
  /*
  curl -XGET http://localhost:8030/api/myEndpoint
    ==> OK

  curl -XPOST http://localhost:8030/api/myEndpoint?myParam=2
    ==> OK

  curl -XPUT http://localhost:8030/api/myEndpoint
    ==> Bad Request (The request contains bad syntax or cannot be fulfilled)

  curl -XPOST http://localhost:8030/api/myEndpoint?newParam=2
    ==> (The request was a legal request, but the server is refusing to respond to it.)
    logs: I have encountered rejections: List(MissingQueryParamRejection(myParam))

  curl -XGET http://localhost:8030/api/otherEndpoint
    ==> Bad Request (The request contains bad syntax or cannot be fulfilled)
   */

  // Having an implicit rejection handler is called sealing a route, which means that no matter what http request you get
  // in your route, you always have a definite action for it

}
