package gn.akka.essentials.part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

class TimedAssertionsSpec
    extends TestKit(ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssertionsConfig")))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {
  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import TimedAssertionsSpec._

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])
    "reply with a timely manner" in {
      // time-boxed test
      within(500 millis, 1 second) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid at a reasonable cadence" in {
      within(1 second) {
        workerActor ! "workSequence"

        val results: Seq[Int] = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
          case WorkResult(result) => result
        }

        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(42)) // timeout of 0.3 seconds
        // This test will fail, because of the config inside the 'application.conf' file:
        /*
            specialTimedAssertionsConfig {
              akka.test.single-expect-default = 0.3s
            }
         */
      }
    }
  }
}

object TimedAssertionsSpec {

  case class WorkResult(result: Int)

  // testing scenarios
  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        // long computation
        Thread.sleep(500)
        sender() ! WorkResult(42)
      case "workSequence" =>
        val random = new Random()
        for (_ <- 1 to 10) {
          Thread.sleep(random.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }
}
