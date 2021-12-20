package gn.akka.persistence.part1.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

// 2
object PersistentActorsExercise extends App {

  /*
    Persistent Actor for a voting station
    Keep:
      - the citizens who voted
      - the poll: mapping between a candidate and the number of received votes

    The actor must be able to recover its state if it's shutdown or restarted
   */
  case class Vote(citizenPID: String, candidate: String)
  case class VoteRecorded(id: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {

    val citizens: mutable.Set[String] = new mutable.HashSet[String]()
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def persistenceId: String = "simple-voting-station"

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        /*
          1- create the event
          2- persist the event
          3- handle a state change after persisting is successful

          In this example, we can fuse steps 1 and 2, into one step, because in this case you don't need to class event,
          like 'VoteRecorded', because it's nearly redundant as Vote
         */
        // this control must be done before the 'persist' method, otherwise multiple votes can be persisted for the same
        // citizen
        if (!citizens.contains(vote.citizenPID)) {
          persist(vote) { _ => // This pattern is called "Command Sourcing" (instead of Event Sourcing)
            log.info(s"Persisted: '$vote'")
            handleInternalStateChange(citizenPID, candidate)
          }
        } else {
          log.warning(s"Citizen '$citizenPID' is trying to vote multiple times")
        }
      case "print" =>
        log.info(s"Current state: \nCitizens: $citizens\nPolls: $poll")
    }

    def handleInternalStateChange(citizenPID: String, candidate: String): Unit = {

      citizens.add(citizenPID)
      val votes = poll.getOrElse(candidate, 0)
      poll.put(candidate, votes + 1)

    }
    override def receiveRecover: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        log.info(s"Recovered: '$vote'")
        handleInternalStateChange(citizenPID, candidate)
    }
  }

  val system = ActorSystem("PersistentActorsExercise")
  val votingStation = system.actorOf(Props[VotingStation], "simpleVotingStation")

  val votesMap = Map[String, String](
    "Ging" -> "Netero",
    "Itachi" -> "Netero",
    "Ippo" -> "Takamora",
    "Shisui" -> "Netero",
    "Morel" -> "Netero"
  )

//  votesMap.keys.foreach { citizen =>
//    votingStation ! Vote(citizen, votesMap(citizen))
//  }

  votingStation ! Vote("Ippo", "Takamora") // cheating : voting again
  votingStation ! "print"
}
