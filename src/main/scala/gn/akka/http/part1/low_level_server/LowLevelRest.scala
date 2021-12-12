package gn.akka.http.part1.low_level_server

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.util.Timeout
import gn.akka.http.part1.low_level_server.GuitarDB.{
  AddQuantity,
  CreateGuitar,
  FindAllGuitars,
  FindGuitarById,
  FindGuitarInStock,
  GuitarCreated
}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/*
 Enhancing the Guitar case class with a quantity field, by default = 0
  - GET to /api/guitar/inventory?inStock=true or false, which returns the guitars in stock as a JSON
  - POST to /api/guitar/inventory?id=X&quantity=Y, which adds Y guitars to the stock for Guitar with id X
 */
case class Guitar(make: String, model: String, quantity: Int = 0)

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
    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add '$quantity' items for guitar '$id'")
      val guitar: Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar // Sending an Option[Guitar]
    case FindGuitarInStock(inStock) =>
      log.info(s"Searching for all guitars either ${if (inStock) "in" else "out"} of stock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity == 0)

  }
}

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitarById(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarInStock(inStock: Boolean)
}

// step2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step3
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)
  // 'jsonFormat3', because 'Guitar' has 3 arguments
}

// 3
// step4: extending 'GuitarStoreJsonProtocol'
object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  /*
      1- GET localhost:8000/api/guitar      => all the guitars in the store
      2- POST localhost:8000/api/guitar     => insert the guitar into the store
      3- GET localhost:8000/api/guitar?id=X => fetches the guitar associated with id X


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
      |  "model": "Stratocaster",
      |  "quantity": 10
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

  def getGuitar(query: Uri.Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]
    guitarId match {
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitarById(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, guitar.toJson.prettyPrint))
        }
      case None => Future(HttpResponse(StatusCodes.NotFound))
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.POST, uri @ Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        // Constructing a http response
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        // 'Option[Guitar]' send back from GuitarDB handling the message 'AddQuantity'
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val inStockOption = query.get("inStock").map(_.toBoolean)
      // Sending a message to the GuitarDB to fetch all guitars in/out of stock
      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, guitars.toJson.prettyPrint))
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))

      }

    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitar"), _, _, _) =>
      /*
        Query parameter handling code here:
        eg: localhost:8010/api/guitar?param1=value1&param2=value2
        These query parameters in Akka HTTP are stored in some kind of a Map, in the form of Query Object
       */
      val query = uri.query() // Query object, similar to a Map[String, String]

      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        // '(guitarDb ? FindAllGuitars)' returns a Future and it can't be type, so we add 'mapTo[List[Guitar]]' to type it
        guitarsFuture.map { guitars =>
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, guitars.toJson.prettyPrint))
        }
        // So basically here, we're trying to construct a Future[HttpResponse] from the 'Future[List[Guitar]]'.
      } else {
        // fetch guitar associated to guitar id
        // localhost:8010/api/guitar?id=2
        getGuitar(query)
      }

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
  // curl -XPOST -H "Content-Type: application/json" http://localhost:8010/api/guitar
  // curl -XPOST -H "Content-Type: application/json" http://localhost:8010/api/guitar  --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/http/guitars.json"
  // curl -XPOST -H "Content-Type: application/json" "http://localhost:8010/api/guitar/inventory?id=1&quantity=5" // add "" because '&' is a special character
  // curl -XPOST -H "Content-Type: application/json" http://localhost:8010/api/guitar/inventory?inStock=false
  // curl -XPOST -H "Content-Type: application/json" http://localhost:8010/api/guitar/inventory?inStock=true

}
