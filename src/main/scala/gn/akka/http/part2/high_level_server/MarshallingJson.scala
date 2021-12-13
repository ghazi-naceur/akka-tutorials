package gn.akka.http.part2.high_level_server

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.util.Timeout
import gn.akka.http.part2.high_level_server.GameAreaMap.AddPlayer

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

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
      players = players - (player.nickname -> player)
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

// 5
object MarshallingJson extends App {
  implicit val system: ActorSystem = ActorSystem("MarshallingJson")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._
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
          // todo 1 get all the players with character class
          reject
        } ~
          (path(Segment) | parameter('nickname)) { nickname =>
            // todo 2 get player by nickname
            reject
          } ~
          pathEndOrSingleSlash {
            // todo 3 get all players
            reject
          }
      } ~
        post {
          // todo 4 add player
          reject
        } ~
        delete {
          // todo 5 delete player
          reject
        }
    }
}
