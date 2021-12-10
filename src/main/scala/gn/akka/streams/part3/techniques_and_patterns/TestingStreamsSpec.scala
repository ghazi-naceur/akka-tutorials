package gn.akka.streams.part3.techniques_and_patterns

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 5
class TestingStreamsSpec extends TestKit(ActorSystem("TestingAkkaStreams")) with WordSpecLike with BeforeAndAfterAll {

  implicit val materializer: Materializer = Materializer.matFromSystem

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with test actors via materialized values" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val probe = TestProbe()
      import akka.pattern.pipe
      import system.dispatcher
      // 'simpleSource.toMat(simpleSink)(Keep.right).run()' returns a Future, so we can pipe it to a Test Probe using
      // Akka 'pipe'
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)
      probe.expectMsg(55)
    }

    "integrate with a test-actor-based sink" in {
      val simpleSource = Source(1 to 5)
      // '.scan()' is similar to fold, but it emits as well the intermediate results
      val simpleFlow = Flow[Int].scan[Int](0)(_ + _) // 0,1,3,6,10,15
      val streamUnderTest = simpleSource.via(simpleFlow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completionMessage")

      streamUnderTest.to(probeSink).run()
      probe.expectMsgAnyOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with streams TestKit" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)
      val testSink = TestSink.probe[Int]
      val materializedTestValue = sourceUnderTest.runWith(testSink)
      // 'runWith' will automatically select the MatVal of 'testSink'
      materializedTestValue
        .request(5)
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete() // expecting termination of the stream
      // The instruction above will send a signal to the 'testSink' requesting some demand from the 'sourceUnderTest'
    }

    "integrate with streams TestKit Source" in {
      val sinkUnderTest = Sink.foreach[Int] {
        case 8 => throw new RuntimeException("It's 8")
        case _ =>
      }

      val testSource = TestSource.probe[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run() // MatVal is a tuple between source and sink
      val (testPublisher, resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(8)
        .sendComplete()

      import system.dispatcher
      resultFuture.onComplete {
        case Success(_) => fail("The sink under test should have thrown an exception on '8'")
        case Failure(_) => // ok
      }
    }

    "test flows with a test source AND a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materializedValue = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materializedValue

      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()

      subscriber
        .request(4) // don't forget requesting messages to run test
        .expectNext(2, 10, 84, 198)
        .expectComplete()

    }
  }

}
