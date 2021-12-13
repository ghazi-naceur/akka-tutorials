package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personJson: RootJsonFormat[Person] = jsonFormat2(Person)
}

// 4
object HighLevelExercise extends App with PersonJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("HighLevelExercise")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  /**
    * Exercise:
    *  - GET /api/people: retrieves all the people you have registered
    *  - GET /api/people/pin: retrieve the person as JSON with that pin
    *  - GET /api/people?pin=X: (same as the previous request)
    *  - POST /api/people  with a JSON payload denoting a Person, and add that person to your database
    *
    *  Recommendations:
    *    - Add JSON support for Person case class
    *    - Set up the server route early, and then implement each endpoint what is about
    *    - For the POST request: Extract the HTTP Request's payload (entity), and process the entity's data
    *
    */

  var people = List(Person(1, "Isaac"), Person(2, "Shisui"), Person(3, "Itachi"))
  println(people.toJson.prettyPrint)

  val personServerRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { pin =>
          complete(HttpEntity(ContentTypes.`application/json`, people.find(_.pin == pin).toJson.prettyPrint))
        } ~
          pathEndOrSingleSlash {
            complete(HttpEntity(ContentTypes.`application/json`, people.toJson.prettyPrint))
          }
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val entity = request.entity
          val strictEntityFuture = entity.toStrict(2 seconds)
          val personFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[Person])
          // side-effect
          /*
          personFuture.onComplete {
            case Success(person) =>
              log.info(s"Storing person: '$person'")
              people = people :+ person
            case Failure(exception) =>
              log.info(s"Error occurred when fetching the person from the entity: $exception")
          }
          complete(personFuture.map(_ => StatusCodes.OK).recover {
            case _ => StatusCodes.InternalServerError
          })
           */
          // or simply when working with Future, use 'onComplete':
          // More powerful, because it gives the chance to return one of 2 possible directives depending on the Future result:
          onComplete(personFuture) {
            case Success(person) =>
              log.info(s"Storing person: '$person'")
              people = people :+ person
              complete(StatusCodes.OK)
            case Failure(exception) =>
              failWith(exception)
          }
        }
    }

  Http().bindAndHandle(personServerRoute, "localhost", 8000)

  /*
    curl -XGET http://localhost:8000/api/people
    curl -XGET http://localhost:8000/api/people/1
    curl -XGET http://localhost:8000/api/people?pin=1
    curl -XPOST http://localhost:8000/api/people -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/http/people.json"
   */
}
