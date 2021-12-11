package gn.akka.http.part1.low_level_server

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.util.Timeout
import gn.akka.http.part1.low_level_server.GuitarDB.{CreateGuitar, FindAllGuitars, GuitarCreated}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

case class Guitar(make: String, model: String)

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._
  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars...")
      sender() ! guitars.values.toList
    case FindGuitarById(id) =>
      log.info(s"Searching guitar by id: '$id'")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar: '$guitar' with id '$currentGuitarId'")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
  }
}

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitarById(id: Int)
  case object FindAllGuitars
}

// step2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step3
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat2(Guitar)
  // 'jsonFormat2', because 'Guitar' has 2 arguments
}

// 3
// step4: extending 'GuitarStoreJsonProtocol'
object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  /*
      GET localhost:8000/api/guitar => all the guitars in the store
      POST localhost:8000/api/guitar => insert the guitar into the store

      marshalling: the process of serializing data to a wire format that the HTTP  client can understand
      CASE CLASS --(marshalling/serializing)--> JSON
      JSON --(unmarshalling/deserializing)--> CASE CLASS
   */

  // step1
  import spray.json._
  // marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster"
      |}
      |""".stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  // Setup:
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibson", "Les Paul"), Guitar("Martin", "LX1"))
  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  // Server code:
  // We'll be interacting with an Actor acting like a database, so we need to use an asynchronous code, which accepts
  // a 'HttpRequest' and sends back a 'Future[HttpResponse]'. In general, each time you need to interact with external
  // services use 'Future', otherwise the response time will be very bad
  implicit val timeout: Timeout = Timeout(2 seconds)
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/guitar"), _, _, _) =>
      val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
      // '(guitarDb ? FindAllGuitars)' returns a Future and it can't be type, so we add 'mapTo[List[Guitar]]' to type it
      guitarsFuture.map { guitars =>
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, guitars.toJson.prettyPrint))
      }
    // So basically here, we're trying to construct a Future[HttpResponse] from the 'Future[List[Guitar]]'.

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity: HttpEntity, _) =>
      // 'entity' is a stream-based data structure modeled as a 'Source[ByteString]'.. This might a problem when trying
      // to process this entity. The below code is the solution for that:
      // Usually 'entity' is small and it can be brought in memory:
      val strictEntityFuture = entity.toStrict(3 seconds)
      // Akka Http will attempt to bring all the content of this entity into memory from the Http connection during 3 seconds
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String // expected json
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        // 'strictEntityFuture' is a future, that calls inside another future '(guitarDb ? CreateGuitar(guitar))', that's
        // why we flatMapped 'strictEntityFuture'
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }

    // This case must be set, in order to gain time, because accumulating Unknown requests without responding to them
    // would be considered as a Backpressure, which will make our Akka streams slower, and will reflect on the TCP layer
    // so all subsequent HTTP request will become slower and slower
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8010)

  //Run app and execute this curl in your terminal:
  // curl -XPOST -H "Content-Type: application/json" http://localhost:8010/api/guitar  --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/http/guitars.json"
}
