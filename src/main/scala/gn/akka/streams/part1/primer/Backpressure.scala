package gn.akka.streams.part1.primer

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Backpressure extends App {

  // Elements flow as response to a demand from consumers
  // Fast consumers = meaning all is well, because the consumers are processing elements as soon as they are available
  // Slow consumers = problem, because the upstream is producing elements faster than the consumer can process them
  //    In this case, a special protocol will kick in, in which the consumer will send a signal to the producer to slow
  //    down, which will make the upstream to limit its elements production
  // Backpressure protocol is transparent and we can't control it

  implicit val system: ActorSystem = ActorSystem("Backpressure")
  import Materializer.matFromSystem

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // Simulating long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }
//  fastSource.to(slowSink).run() // fusion operators ?!
  // This is not a Backpressure example. This is a operator fusion and all the components run in the same actor, so
  // each element will be printed after 1 second (as the long simulation states in the slowSink)

//  fastSource.async.to(slowSink).run()
  // Each element is printed 1 by 1 each second using asynchronous components ==> This is actual Backpressure
  // The 'fastSource' and the 'slowSink' operate in separate actors, so it must be some sort of protocol in place to
  // slow down the fast source: The Backpressure protocol

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming $x")
    x + 1
  }

  /**
  fastSource.async
    .via(simpleFlow)
    .async
    .to(slowSink)
    .run()
    */
  // The result will contain a batch of "Incoming..." elements, after that a batch of "Sink..." or "Processed", and back
  // to anther batch of "Incoming..." and then "Sink..."
  // The 'slowSink' sends backpressure signal upstream. The 'simpleFlow', when it receives that backpressure signal, instead
  // of forwarding it to the 'simpleSource', it buffers internally a number of elements, until it has enough.
  // The default number off elements in the Akka Streams Buffer is 16 elements (that's why we found 16 "Incoming" elements
  // in each batch).
  // After the 'simpleSink' processed some elements, the 'simpleFlow' allowed more elements to go through and it buffered
  // them again, because the 'simpleSink' is still slow, and this happens a bunch of times

  /**
    * Reactions to Backpressure in order:
    * - try to slow down if possible
    * - buffer elements until there's more demand (our last example)
    * - drop down elements from the buffer if it overflows
    * - teardown/kill the whole stream (failure)
    *
    * We can control the case when the buffer has buffered enough elements and it's about to overflow:
    */
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  // 'OverflowStrategy.dropHead' will drop the oldest element in the buffer to make room for the new one
  /*
  The 'bufferedFlow' will receive all the 1000 elements from the 'simpleSource'.
  We choose a buffer size of 10 (instead of 16), and an OverflowStrategy that will drop the head. As a result most of
  the elements will be dropped, and the elements that got to the sink are '2 to 17' and '992 to 1001'.
  The last 10 elements (992 to 1001) are easy to explain, because they are the newest elements that exit the flow.
  The reason why the the elements '2 to 17' were printed out, because these numbers were buffered at the sink.
  In fact, the sink buffers the first 16 numbers, and the flow buffers the last 10 numbers, which ended up in the sink

  1-16: no one backpressured
  17-26: flow will buffer, but at the time and because the source is so fast, the sink doesn't even have the chance to
      print the first result, so  the flow will start dropping at the next element. In other terms, from '26 to 1000'
      the flow will always drop the oldest element.
      So the content of the buffer of the flow will be '991 to 1000', which will be transformed to '992 to 1001' and
      then will be sent to the sink. And because the Sink is so slow, it will begin to process the elements in its buffer
      2 to 17, after that it will process 992 to 1001, each element each second
   */
  /**
  fastSource.async
    .via(bufferedFlow)
    .async
    .to(slowSink)
    .run()
    */
  /*
  Overflow strategies:
    - drop head = oldest
    - drop tail = newest
    - drop new = the exact element to be added == keeps the buffer
    - drop the entire buffer
    - emit backpressure signal
    - fail
   */

  // Throttling: allows to manually trigger backpressure
  fastSource
    .throttle(2, 1 second) // emits at most 2 elements per second
    .runWith(Sink.foreach(println))
}
