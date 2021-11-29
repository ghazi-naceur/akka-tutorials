package gn.akka.essentials.part2.testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import gn.akka.essentials.part2.testing.TestProbeSpec.Master.{
  Register,
  RegistrationAck,
  Report,
  SlaveWork,
  Work,
  WorkCompleted
}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

// 2
class TestProbeSpec
    extends TestKit(ActorSystem("TesProbeSpec"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // fictitious actor with asserts capabilities
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to slave actor" in {
      val master = system.actorOf(Props[Master])
      // We'll recreate the master each time, because the master is stateful in a way, because it keeps the 'totalWordCount'

      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "Learning akka"
      master ! Work(workloadString)
      // testActor is the sender of 'master ! Work(workloadString)'

      // The interaction between the master and the slave actors
      slave.expectMsg(SlaveWork(workloadString, testActor))

      slave.reply(WorkCompleted(2, testActor))
      expectMsg(Report(2)) // The 'testActor' will receive this Report message
    }

    "Aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
      val workloadString = "Learning akka"
      master ! Work(workloadString)
      master ! Work(workloadString)

      // In the meantime, I don't have a slave actor
      slave.receiveWhile() {
        case SlaveWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(2, testActor))
        // we're asserting here, that we need to receive the exact same workload and same requester, and sending
        // the same response WorkCompleted each time we receive the thing
      }
      expectMsg(Report(2))
      expectMsg(Report(4)) // aggregate result 2+2
    }
  }
}

object TestProbeSpec {
  /*
  Word counting actor hierarchy master-slave
  Send some work to the master:
    - master sends the slave some work
    - slave processes the work and replies to master
    - master aggregates the result
  Master sends the total count to the original requester
   */

  class Master extends Actor {
    import Master._
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _ => // ignore
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(slaveRef, newTotalWordCount))
    }
  }
  object Master {
    case class Register(slaveRef: ActorRef)
    case object RegistrationAck
    case class Work(text: String)
    case class SlaveWork(text: String, originalRequester: ActorRef)
    case class WorkCompleted(count: Int, originalRequester: ActorRef)
    case class Report(totalWordCount: Int)
  }
}
