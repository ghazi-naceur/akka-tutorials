package gn.akka.essentials.part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import gn.akka.essentials.part2.testing.SynchronousTestingSpec.{Counter, Inc, Read}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class SynchronousTestingSpec extends WordSpecLike with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("SynchronousTestingSpec")

  override protected def afterAll(): Unit = system.terminate()

  "A counter" should {
    "synchronously increase its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Inc
      // Counter has already received the message, because sending a message for 'TestActorRef' happens in the call you
      // thread.

      assert(counter.underlyingActor.count == 1) // 'counter.underlyingActor' is the Actor instance
    }
//    'TestActorRef' is so powerful, that it can invoke the receive handler on the 'underlyingActor' directly
    "synchronously increase its counter at the call of the receive function" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Inc)
      // the same thing as sending a message, because sending a message already happening in the same thread.
      assert(counter.underlyingActor.count == 1)
    }
    // 'dispatcher' the communication with the actor happens in the calling thread
    "work on the calling thread dispatcher" in {
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      // This counter will run on thread dispatcher, which means that whatever message I send to this actor 'counter'
      // will happen in the calling thread
      val probe = TestProbe()
      probe.send(counter, Read)
      // 'probe', basically, receives 0 'count' as a result.
      // A magical thing happens, because due the fact that the 'counter' operates on the calling thread dispatcher
      // after the line 'probe.send(counter, Read)', the 'probe' has already received the 'count' reply, because every
      // single interaction with the counter happens on the 'probe' calling thread:
//      probe.expectMsg(0) // the 'probe' has already received 0, because this interaction 'probe.send(counter, Read)'
      // already happen.
      // We can rewrite as well:
      probe.expectMsg(Duration.Zero, 0) // and the timeout is 'Duration.Zero', meaning that there is no timeout
    }
  }
}

object SynchronousTestingSpec {

  case object Inc
  case object Read

  class Counter extends Actor {
    var count = 0
    override def receive: Receive = {
      case Inc  => count += 1
      case Read => sender() ! count
    }
  }
}
