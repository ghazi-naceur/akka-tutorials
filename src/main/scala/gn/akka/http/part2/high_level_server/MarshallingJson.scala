package gn.akka.http.part2.high_level_server

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.pattern.ask
import akka.util.Timeout
import gn.akka.http.part2.high_level_server.GameAreaMap.AddPlayer

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// step1
import spray.json._

case class Player(nickname: String, characterClass: String, level: Int)

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._
  var players: Map[String, Player] = Map[String, Player]()
  override def receive: Receive = {
    case GetPlayers =>
      log.info("Fetching all players...")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"Fetching player with nickname: '$nickname'")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Getting all players with the same character class: '$characterClass'")
      sender() ! players.values.toList.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add the player: '$player'")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to remove the player: '$player'")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}
object GameAreaMap {
  case object GetPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

// step2
trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat: RootJsonFormat[Player] = jsonFormat3(Player)
}

// 5
object MarshallingJson
    extends App
    // step3: with PlayerJsonProtocol
    with PlayerJsonProtocol
    // step4: with SprayJsonSupport
    with SprayJsonSupport {

  implicit val system: ActorSystem = ActorSystem("MarshallingJson")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)

  import GameAreaMap._

  val gameMapActor = system.actorOf(Props[GameAreaMap], "gameMap")
  val playersList = List(
    Player("Isaac", "Hunter", 900),
    Player("Shisui", "Shinobi", 800),
    Player("Takamora", "Boxer", 500),
    Player("Morel", "Hunter", 600),
    Player("Itachi", "Shinobi", 800)
  )
  playersList.foreach { player =>
    gameMapActor ! AddPlayer(player)
  }

  /*
    - GET /api/player: returns all players in the map, as JSON
    - GET /api/player/(nickname): returns a player with the given nickname, as JSON
    - GET /api/player?nickname=X, as previous
    - GET /api/player/class/(charClass): charClass is a variable.. returns all players with the given class
    - POST /api/player, with JSON payload, which adds the player to the Map
    - DELETE /api/player, with JSON playload, which removes the player from the Map
   */

  val gameRouteSkeleton =
    pathPrefix("api" / "player") {
      get {
        // 'Segment' to extract a string
        path("class" / Segment) { characterClass =>
          val playersByClassFuture = (gameMapActor ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          // step5: Now 'playersByClassFuture: Future[List[String]]' can be converted to JSON to be a HttpResponse
          complete(playersByClassFuture)
        } ~
          (path(Segment) | parameter('nickname)) { nickname =>
            val playerOptionFuture = (gameMapActor ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playerOptionFuture)
          } ~
          pathEndOrSingleSlash {
            complete((gameMapActor ? GetPlayers).mapTo[List[Player]])
          }
      } ~
        post {
          // 'as[Player]' is an extraction directive that not only extracts the HttpEntity out of the HttpRequest, but
          // it converts its payload to the 'Player' data structure. This directive is doing some implicit conversions
          // behind the scenes. It takes a 'FromRequestUnmarshaller' type as parameter.
          // 'as[Player]' can be rewritten as follows: 'implicitly[FromRequestUnmarshaller[Player]]'
          // 'implicitly' fetches the implicit value for the class inside the brackets, which is 'FromRequestUnmarshaller[Player]'
          // and we can pass that as an actual value to the 'entity' directive

          // The 'entity' directive takes a parameter with type 'FromRequestUnmarshaller', which is in our case, the
          // implicit 'playerFormat' inside the PlayerJsonProtocol trait. The 'playerFormat' is both 'Marshallable' and
          // 'Unmarshaller', so it converts to and from JSON.
          // The 'complete' directive takes a parameter with type 'ToResponseMarshallable'
          // The 'entity' and the 'complete' directives are symmetrical or opposites.
          entity(as[Player]) { player =>
            complete((gameMapActor ? AddPlayer(player)).map(_ => StatusCodes.OK))
          }
        } ~
        delete {
          entity(as[Player]) { player =>
            complete((gameMapActor ? RemovePlayer(player)).map(_ => StatusCodes.OK))
          }
        }
    }

  Http().bindAndHandle(gameRouteSkeleton, "localhost", 8010)

  /*
      curl -XGET http://localhost:8010/api/player/class/Hunter
      curl -XGET http://localhost:8010/api/player/Isaac
      curl -XGET http://localhost:8010/api/player?nickname=Isaac
      curl -XGET http://localhost:8010/api/player
      curl -XPOST http://localhost:8010/api/player -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/http/player.json"
      curl -XDELETE http://localhost:8010/api/player -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/http/player.json"
   */
}
