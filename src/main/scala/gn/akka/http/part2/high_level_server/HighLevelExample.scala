package gn.akka.http.part2.high_level_server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.pattern.ask
import akka.util.Timeout
import gn.akka.http.part1.low_level_server.GuitarDB.{CreateGuitar, FindAllGuitars, FindGuitarById, FindGuitarInStock}
import gn.akka.http.part1.low_level_server.{Guitar, GuitarDB, GuitarStoreJsonProtocol}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("HighLevelExample")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  /*
  Another solution for the exercise in "LowLevelRest.scala", using this time, the High Level API:

    GET /api/guitar fetches all the guitars in the store
    GET /api/guitar?id=X fetches the guitar with the id = X
    GET /api/guitar?X fetches the guitar with the id = X
    GET /api/guitar/inventory?inStock=true
   */

  // Setup:
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibson", "Les Paul"), Guitar("Martin", "LX1"))
  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  val guitarServerRoute = {
    path("api" / "guitar") {
      // The more specific request should be put first
      parameter('id.as[Int]) { (guitarId: Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(ContentTypes.`application/json`, guitarOption.toJson.prettyPrint)
          }
          complete(entityFuture)
        }
      } ~
        get {
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
          val entityFuture = guitarsFuture.map { guitars =>
            HttpEntity(ContentTypes.`application/json`, guitars.toJson.prettyPrint)
          }
          complete(entityFuture)
        }
    }
  } ~
    path("api" / "guitar" / IntNumber) { (guitarId: Int) =>
      get {
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
        val entityFuture = guitarFuture.map { guitarOption =>
          HttpEntity(ContentTypes.`application/json`, guitarOption.toJson.prettyPrint)
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / "inventory") {
      get {
        parameter('inStock.as[Boolean]) { inStock =>
          val guitarFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarInStock(inStock)).mapTo[List[Guitar]]
          val entityFuture = guitarFuture.map { guitars =>
            HttpEntity(ContentTypes.`application/json`, guitars.toJson.prettyPrint)
          }
          complete(entityFuture)
        }
      }
    }

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FindGuitarInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
        (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
          complete(
            (guitarDb ? FindGuitarById(guitarId))
              .mapTo[Option[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        } ~
        pathEndOrSingleSlash {
          complete(
            (guitarDb ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
    }

  def toHttpEntity(payload: String): HttpEntity.Strict = HttpEntity(ContentTypes.`application/json`, payload)

  Http().bindAndHandle(guitarServerRoute, "localhost", 8000)
  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8010)

  /*
      curl -XGET http://localhost:8000/api/guitar
      curl -XGET http://localhost:8010/api/guitar

      curl -XGET http://localhost:8000/api/guitar?id=2
      curl -XGET http://localhost:8010/api/guitar?id=2

      curl -XGET http://localhost:8000/api/guitar/2
      curl -XGET http://localhost:8010/api/guitar/2

      curl -XGET http://localhost:8000/api/guitar/inventory?inStock=false
      curl -XGET http://localhost:8010/api/guitar/inventory?inStock=false

      curl -XGET http://localhost:8000/api/guitar/inventory?inStock=true
      curl -XGET http://localhost:8010/api/guitar/inventory?inStock=true
   */
}
