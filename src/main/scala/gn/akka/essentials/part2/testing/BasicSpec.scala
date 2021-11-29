package gn.akka.essentials.part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import gn.akka.essentials.part2.testing.BasicSpec.{BlackHole, LabTestActor, SimpleActor}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

// 1
class BasicSpec extends TestKit(ActorSystem("BasicSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello test"
      echoActor ! message

      expectMsg(message) // akka.test.single-expect-default: time to wait for the expected message (default: 3s)
    }
  }
  "A black hole actor" should {
    "send back the same message" in {
      val blackHole = system.actorOf(Props[BlackHole])
      val message = "hello test"
      blackHole ! message

      expectNoMessage(1 second)
      // The 'testActor' is used for the communication that we want to test.
      // The 'testActor' is passed implicitly as the sender of every single message that we send, because we mixed in the
      // trait 'ImplicitSender'.
    }
  }

  // Message assertions
  "A lab test actor" should {
    val labTestActor = system.actorOf(Props[LabTestActor])
    "turn a string to uppercase" in {
//      labTestActor ! "This is a message"
//      expectMsg("THIS IS A MESSAGE")
      // we can as well:
      labTestActor ! "This is a message"
      val reply = expectMsgType[String]
      assert(reply == "THIS IS A MESSAGE")
    }

    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "reply with favorite tech" in {
      labTestActor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }

    "reply with cool tech in a different way" in {
      labTestActor ! "favoriteTech"
      val messages = receiveN(2) // Seq[Any]
      // free to do more complicated assertions
    }

    "reply with cool tech in a fancy way" in {
      labTestActor ! "favoriteTech"
      // expected with a Partial Function
      expectMsgPF() {
        case "Scala" => // only care that Partial Function is defined
        case "Akka"  =>
      }
    }
  }
}

object BasicSpec {
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }
  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }
  class LabTestActor extends Actor {
    val random = new Random()
    override def receive: Receive = {
      case "greeting" => if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase
    }
  }
}
