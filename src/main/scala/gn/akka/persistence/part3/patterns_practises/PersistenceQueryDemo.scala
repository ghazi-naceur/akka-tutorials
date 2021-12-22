package gn.akka.persistence.part3.patterns_practises

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

/**
  * This example won't work because I upgraded the Akka version from 2.5.x to 2.6.x
  */

// 3
object PersistenceQueryDemo extends App {

  /*
    Queries used to read data from persistence stores:
      - select persistence IDs
      - select events by persistence ID
      - select events across persistence IDs, by tags

    Use cases:
      - which persistent actors are active
      - recreate older states
      - track how we arrived to the current state
      - data processing on events in the entire store
   */

  implicit val system: ActorSystem =
    ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQuery"))
  import akka.stream.Materializer.matFromSystem

  // read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // 1- Persistence IDs:
  // Get all persistence IDs available in the journal. This query will return the newly added persistence id, even after
  // executing another chunk of the code ==> using an Akka Stream internally
  // If you want only the current persistence ids and you don't need to keep this query listening on the new ones, just
  // use 'readJournal.currentPersistenceIds()' (the stream will be closed after executing the command)
  val persistenceIds = readJournal.persistenceIds()

  /**
  persistenceIds.runForeach { persistenceId =>
    println(s"Found persistence ID: '$persistenceId'")
  }
    */
  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query-id-1"

    override def receiveCommand: Receive = {
      case m =>
        persist(m) { _ =>
          log.info(s"Persisted: '$m'")
        }
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered: '$e'")
    }
  }

  val simpleActor = system.actorOf(Props[SimplePersistentActor], "simplePersistentActor")
  import system.dispatcher

  /**
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "This is a message"
    simpleActor ! message
  }
    */

  // 2- Events by Persistence ID:
  // returning events in the same order that they were persisted
  // Live - Stream:
  val events = readJournal.eventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: '$event'")
  }
  // Only current:
  readJournal.currentEventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "This is a second message"
    simpleActor ! message
  }

  // 3- Events by Tags:
  // won't return by order, because it searches across the table
  val genres = Array("AAA", "BBB", "CCC", "DDD", "EEE")
  case class Song(artist: String, title: String, genre: String)
  // command
  case class PlayList(songs: List[Song])
  // event
  case class PlayListPurchase(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {

    var latestPlaylistId = 0

    override def persistenceId: String = "music-store-checkout"

    override def receiveCommand: Receive = {
      case PlayList(songs) =>
        persist(PlayListPurchase(latestPlaylistId, songs)) { _ =>
          log.info(s"User purchased: '$songs'")
          latestPlaylistId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlayListPurchase(id, _) =>
        log.info(s"Recovered: $event")
        latestPlaylistId = id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {

    override def manifest(event: Any): String = "MS"

    override def toJournal(event: Any): Any =
      event match {
        case event @ PlayListPurchase(_, songs) =>
          val genres = songs.map(_.genre).toSet
          Tagged(event, genres) // Tagged is a wrapper for the event and a set of strings attached to the event
        // tags is mentioned inside the Cassandra table 'akka.messages'
        case event => event
      }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  val random = new Random
  for (_ <- 1 to 10) {
    val maxSongs = random.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(random.nextInt(5))
      Song(s"Artist $i", s"Song $i", randomGenre)
    }
    checkoutActor ! PlayList(songs.toList)
  }

  // Live
  val aaaPlaylists = readJournal.eventsByTag("AAA", Offset.noOffset)
  aaaPlaylists.runForeach { event =>
    println(s"Found playlist with an AAA song: '$event'")
  }
  // Current
  readJournal.currentEventsByTag("AAA", Offset.noOffset)
}
