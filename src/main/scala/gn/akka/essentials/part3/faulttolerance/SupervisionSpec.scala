package gn.akka.essentials.part3.faulttolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{
  Actor,
  ActorRef,
  ActorSystem,
  AllForOneStrategy,
  OneForOneStrategy,
  Props,
  SupervisorStrategy,
  Terminated
}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

// 4
class SupervisionSpec
    extends TestKit(ActorSystem("SupervisionSpec"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {
  // It's fine that Actors crash, but it's up to the Parents to decide upon their children failure.
  /*
  When an Actor fail, it:
    - suspends all of its children
    - sends a spacial message to its parent, via a dedicated message queue

  Afterwards, the parent can decide what to do with this failure:
    - It can resume the actor
    - It can restart the actor and clear its internal state, which by default stops all its children (default behavior)
    - It can stop the actor all together, which means stopping everything beneath it
    - It can decide to fail itself, and escalate the failure to its parent
   */

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import SupervisionSpec._

  "A supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is interesting"
      child ! Report
      expectMsg(3)

      child ! "This is a very very very very very very very very very very long message"
      child ! Report
      expectMsg(3) // state didn't change, so the count should remain equal to 3
    }

    "restart its child in case of an empty sentence" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is interesting"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0) // It won't cumulate because it's restarted
    }

    "terminate its child in case pf a major error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! "lowercase"
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate an error when it doesn't know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! 43
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
      /*
      [ERROR] [12/01/2021 14:34:01.241] [SupervisionSpec-akka.actor.default-dispatcher-3] [akka://SupervisionSpec/user/supervisor] Can only receive strings
      // Error at Parent level
      The escalate strategy escalates the exception to the UserGuardian, and the UserGuardian default strategy is to restart
      everything, the supervisor restart method will kill the FussyWordCounter.
       */
    }
  }

  "A kinder supervisor" should {
    "not kill children in case in it's restarts or escalates failures" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "noDeathSupervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is interesting"
      child ! Report
      expectMsg(3)

      child ! 43 // The exception will be signaled to the parent which is the 'NoDeathOnRestartSupervisor'|'Supervisor'
      // (where we define the new supervisor strategy which different kinds of exceptions), so in case of 'Exception'
      // (number instead of string), we have the Akka directive Escalate, so 'NoDeathOnRestartSupervisor' will 'Escalate'
      // to the 'UserGuardian'. The 'UserGuardian' will restart the supervisor 'NoDeathOnRestartSupervisor', but on the
      // method 'preRestart()' of 'NoDeathOnRestartSupervisor', I won't kill the child (preRestart() is empty), so we'll
      // expect that the child will still be alive, and we'll to send it a message:

      child ! Report
      expectMsg(0)
    }
  }
  "An all-for-one supervisor" should {
    "apply the all-for-one strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "allForOneSupervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      supervisor ! Props[FussyWordCounter]
      val secondChild = expectMsgType[ActorRef]

      secondChild ! "Akka is interesting"
      secondChild ! Report
      expectMsg(3)

      EventFilter[NullPointerException]() intercept {
        child ! ""
      }
      Thread.sleep(500) // A sleep need for the supervisor to apply the all-for-one strategy
      // We're expecting that the second child will be restarted, so the number of expected messages will be 0
      secondChild ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  case object Report

  class Supervisor extends Actor {

    override def supervisorStrategy: SupervisorStrategy =
      OneForOneStrategy() {
        case _: NullPointerException     => Restart // Akka Directive
        case _: IllegalArgumentException => Stop
        case _: RuntimeException         => Resume
        case _: Exception                => Escalate
      }

    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef // send back actor reference to use it later
    }
  }

  class FussyWordCounter extends Actor {
    var words = 0
    override def receive: Receive = {
      case Report => sender() ! words
      case ""     => throw new NullPointerException("Sentence is empty")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException("Sentence is too long")
        else if (!Character.isUpperCase(sentence(0)))
          throw new IllegalArgumentException("Sentence must start with an uppercase")
        else words += sentence.split(" ").length
      case _ => throw new Exception("Can only receive strings")
    }
  }

//  Let's assume that the supervisor actor didn't kill all of its children.
  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {}
  }

  class AllForOneSupervisor extends Supervisor {
    override def supervisorStrategy: SupervisorStrategy =
      AllForOneStrategy() {
        case _: NullPointerException     => Restart // Akka Directive
        case _: IllegalArgumentException => Stop
        case _: RuntimeException         => Resume
        case _: Exception                => Escalate
      }
  }
  // The difference between OneForOneStrategy and AllForOneStrategy is that the OneForOneStrategy applies the pattern matching
  // cases on the exact Actor that cause the failure. Whereas, AllForOneStrategy applies the cases for all the Actors, regardless
  // of the one who caused the failure. So in case, one child fails with an exception, all the other children will be subject
  // to the same supervision directive.
}
