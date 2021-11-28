package gn.akka.essentials.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

// 4
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
      case Food(VEGETABLES) => context.become(sadReceive, discardOld = false)
//        context.unbecome()
      case Food(CHOCOLATE) =>
//        context.become(happyReceive, discardOld = false)
      // discardOld = true: discard the old message handler == fully replace the old message handler
      // discardOld = false: stack the new message handler into a stack of message handlers
      case Ask(_) => sender() ! Accept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive, discardOld = false)
      case Food(CHOCOLATE)  => context.unbecome()
      case Ask(_)           => sender() ! Reject
    }

  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._
    override def receive: Receive = {
      case Start(kid) =>
        kid ! Food(VEGETABLES)
        kid ! Food(VEGETABLES)
        kid ! Food(CHOCOLATE)
        kid ! Food(CHOCOLATE)
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

    /**
      * 1- With context become:
      *
      * - Input :
      * Food(veg, false) -> message handler turns to 'sadReceive' => stack.push(sadReceive)
      * Food(choco, false) -> message handler turns to 'happyReceive' => stack.push(happyReceive)
      *
      * - Result:
      * Stack: (read from bottom to top)
      * 1- happyReceive
      * 2- sadReceive
      * 3- initiated as happyReceive
      *
      * To pop the content of this stack to return to the previous state, we use the 'unbecome' method
      *
      * 2- With context unbecome:
      *
      * - Input:
      * Food(veg)
      * Food(veg)
      * Food(choco)
      * Food(choco)
      *
      * - Result:
      * Stack: (read from bottom to top)
      * 1- happyReceive (initiation)
      * -- receiving a 2nd choco, will pop the last sadReceive, and we'll have happyReceive
      * 1- sadReceive
      * 2- happyReceive (initiation)
      * -- Receiving a choco here, will pop the 2- sadReceive (so we'll have 3- sadReceive)
      * 2- sadReceive
      * 3- sadReceive
      * 4- happyReceive (initiation)
      */
  }
}
