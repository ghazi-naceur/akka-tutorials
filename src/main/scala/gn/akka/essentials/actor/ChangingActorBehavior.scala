package gn.akka.essentials.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehavior {

  class FussyKid extends Actor {
    import FussyKid._
    import Mom._
    var state: String = HAPPY
    override def receive: Receive = {
      case Food(VEGETABLES) => state = SAD
      case Food(CHOCOLATE)  => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! Accept
        else sender() ! Reject
    }
  }
  object FussyKid {
    case object Accept
    case object Reject

    val HAPPY = "happy"
    val SAD = "sad"
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive
    def happyReceive: Receive = {
      case Food(VEGETABLES) => // change my receive handler to sadReceive
        context.become(sadReceive)
      case Food(CHOCOLATE) =>
      case Ask(_)          => sender() ! Accept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLES) =>
      case Food(CHOCOLATE) => // change my receive handler to happyReceive
        context.become(happyReceive)
      case Ask(_) => sender() ! Reject
    }

  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._
    override def receive: Receive = {
      case Start(kid) =>
        kid ! Food(VEGETABLES)
        kid ! Ask("Do you want to play?")
      case Accept => println("The kid is happy")
      case Reject => println("The kid is sad")
    }
  }
  object Mom {
    case class Start(kid: ActorRef)
    case class Food(food: String)
    case class Ask(message: String)

    val VEGETABLES = "veggies"
    val CHOCOLATE = "chocolate"
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("app-family")
    val kid = system.actorOf(Props[FussyKid], "fussyKid")
    val statelessKid = system.actorOf(Props[FussyKid], "statelessFussyKid")
    val mom = system.actorOf(Props[Mom], "mom")

    import Mom._
    mom ! Start(kid)
    mom ! Start(statelessKid)

    /**
      * Mom receives Start
      *    statelessKid receives Food(veggies) => statelessKid will change the handler to 'sadReceive'
      *    statelessKid receives Ask(play?) => statelessKid will reply with 'sadReceive' handler
      * Mom receives Reject
      *
      */
  }
}
