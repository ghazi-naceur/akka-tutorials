package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 7
object HandlingExceptions extends App {
  implicit val system: ActorSystem = ActorSystem("HandlingExceptions")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)

  val simpleRoute =
    path("api" / "people") {
      get {
        //  directive that throws an exception
        throw new RuntimeException("Getting all the people took too long")
      } ~
        post {
          parameter('id) { id =>
            if (id.length > 2)
              throw new NoSuchElementException(s"Parameter '$id' cannot be found")

            complete(StatusCodes.OK)
          }
        }
    }

  // There is a default exception handler will kick in, once an exception is thrown, and it will send a HTTP response
  // with the code 500: Internal Server Error. However, we have the ability to choose what should happen if an exception
  // is thrown by a directive

  // overriding the default exception handler
  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: RuntimeException =>
      complete(StatusCodes.NotFound, ex.getMessage)
    case ex: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, ex.getMessage)
  }

  Http().bindAndHandle(simpleRoute, "localhost", 8010)

  /*
  curl -XGET http://localhost:8010/api/people
    ==> 404 (payload: Getting all the people took too long) // payload is the exception message

  curl -XPOST http://localhost:8010/api/people?id=mmls
    (Comment the RuntimeException handling code inside the handler and execute the above http request)
    ==> 500 (Internal Server Error) // The 'NoSuchElementException' thrown by the route wasn't handled in the code by the
      custom handler, so the default handler kicked in and returned the default 500 status code
   */

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: RuntimeException =>
      complete(StatusCodes.NotFound, ex.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: NoSuchElementException =>
      complete(StatusCodes.NotFound, ex.getMessage)
  }

  val delicateHandleRoute = {
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          //  directive that throws an exception
          throw new RuntimeException("Getting all the people took too long")
        } ~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter('id) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException(s"Parameter '$id' cannot be found")

                complete(StatusCodes.OK)
              }
            }
          }
      }
    }
  }

  Http().bindAndHandle(delicateHandleRoute, "localhost", 8020)

  /*
  curl -XGET http://localhost:8020/api/people
    ==> 404 (payload: Getting all the people took too long) // payload is the exception message

  curl -XPOST http://localhost:8020/api/people?id=mmls
    ==> 400 (Parameter 'mmls' cannot be found)
   */

  // Exceptions are not aggregated by the routing tree
}
