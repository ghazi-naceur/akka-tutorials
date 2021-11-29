package gn.akka.essentials.part1.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

// 5
object ChangingBehaviorExercises {

  // Exercise 1: Counter without a mutable state
  class CounterActor extends Actor {
    import CounterActor._
    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case Increment =>
        println(s"Incrementing... Current value is $currentCount")
        context.become(countReceive(currentCount + 1))
      case Decrement =>
        println(s"Decrementing... Current value is $currentCount")
        context.become(countReceive(currentCount - 1))
      case Print => println("The current count: " + currentCount)
    }
  }
  object CounterActor {
    case object Increment
    case object Decrement
    case object Print
  }

  // Exercise 2: Simplified voting system
  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = {
      case Vote(c)           => context.become(voted(c))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])
  class VoteAggregator extends Actor {

    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizen => citizen ! VoteStatusRequest)
        context.become(awaitingStates(citizens, Map()))
    }

    def awaitingStates(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) => // a citizen hasn't voted yet
        sender() ! VoteStatusRequest // this might end up in an infinite loop, if the candidate doesn't vote
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newStillWaiting.isEmpty)
          println(s"Poll stats: $newStats")
        else
          context.become(awaitingStates(newStillWaiting, newStats))
    }
  }

  def main(args: Array[String]): Unit = {
    // Exercise 1
    import CounterActor._
    val system = ActorSystem("counter")
    val counter = system.actorOf(Props[CounterActor], "counter")
    (1 to 10).foreach(_ => counter ! Increment)
    (1 to 7).foreach(_ => counter ! Decrement)
    counter ! Print

    // Exercise 2
    val isaac = system.actorOf(Props[Citizen])
    val shisui = system.actorOf(Props[Citizen])
    val takamora = system.actorOf(Props[Citizen])
    val ging = system.actorOf(Props[Citizen])

    isaac ! Vote("Itachi")
    shisui ! Vote("Itachi")
    takamora ! Vote("Ippo")
    ging ! Vote("Itachi")

    val voteAggregator = system.actorOf(Props[VoteAggregator])
    voteAggregator ! AggregateVotes(Set(isaac, shisui, takamora, ging))

    /**
      * Print the status of the vote
      *
      * Output:
      * Itachi -> 3
      * Ippo -> 1
      */
  }
}
