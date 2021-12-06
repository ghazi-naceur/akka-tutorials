package gn.akka.streams.part1.primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")
  import Materializer.matFromSystem

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

//  simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()
  // This tuns on the same actor
  // This will use a single CPU Core for every single element through the entire flow
  // operator.component fusion: It improves throughput and done behind the scenes by Akka Streams

  // Equivalent behavior, and behind scenes Akka Streams does something similar to this:
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        val x2 = x + 1 // flow 1
        val y = x2 * 10 // flow 2
        println(y) // sink
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
//  (1 to 1000).foreach(simpleActor ! _)

  // Example 2:
  // complex flow:
  val complexFlow = Flow[Int].map { x =>
    // simulating a long computation
    Thread.sleep(1000)
    x + 1
  }
  val complexFlow2 = Flow[Int].map { x =>
    // simulating a long computation
    Thread.sleep(1000)
    x * 10
  }

//  simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()
  // There is a 2 second difference between each result, because the 'complexFlow' and 'complexFlow2' run on the same
  // actor ==> costly and not efficient in this case
  // Solution: Async boundary
  /**
  simpleSource
    .via(complexFlow)
    .async // runs on one actor
    .via(complexFlow2)
    .async // runs on another actor
    .to(simpleSink) // runs on a third actor
    .run()
    */
// There is 1 second time difference between 2 consecutive elements ==> time saving, because each flow in a separate actor
// Doubling the throughput
// Parallel computation
// Async boundary breaks the operator fusion, which is done by default in akka

  // ordering guarantees
  Source(1 to 3)
    .map(i => { println(s"Flow A: $i"); i })
    .map(i => { println(s"Flow B: $i"); i })
    .map(i => { println(s"Flow C: $i"); i })
    .runWith(Sink.ignore)

  /**
    * Fully deterministic ordering guaranteed
    * Result:
        Flow A: 1
        Flow B: 1
        Flow C: 1
        Flow A: 2
        Flow B: 2
        Flow C: 2
        Flow A: 3
        Flow B: 3
        Flow C: 3
    */
  // If we put async, we will loose the ordering for elements, but the println for line 1, will always happen before the
  // println for line 2 and 3:
  Source(1 to 3)
    .map(i => { println(s"Flow A: $i"); i })
    .async
    .map(i => { println(s"Flow B: $i"); i })
    .async
    .map(i => { println(s"Flow C: $i"); i })
    .async
    .runWith(Sink.ignore)

  /**
    * Result:
        Flow A: 1
        Flow A: 2
        Flow B: 1
        Flow C: 1
        Flow B: 2
        Flow A: 3
        Flow C: 2
        Flow B: 3
        Flow C: 3
    */
}
