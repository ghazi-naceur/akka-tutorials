package gn.akka.streams.part2.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 1
object GraphBasics extends App {

  implicit val system: ActorSystem = ActorSystem("GraphIntro")
  import akka.stream.Materializer.matFromSystem

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)
  val output = Sink.foreach[(Int, Int)](println)
  // We want 'incrementer' and 'multiplier' to work in parallel
  // step1- setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      // builder is a mutable data structure
      import GraphDSL.Implicits._ // bringing some operators into scope

      // step2- add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2))
      // fan-out operator, because it has 1 input and multiple outputs (in our case 2 outputs)
      val zip = builder.add(Zip[Int, Int])
      // fan-in operator, because it has multiple inputs (2 inputs in our case), and 1 output

      // step3- tying up the components
      input ~> broadcast // input feeds into broadcast, 1 branch into 'incrementer' and the other into 'multiplier'
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      // step4- return a ClosedShape
      ClosedShape //  Freeze the builder's shape => builder becomes immutable
      // shape
    } // static graph
  ) // runnable graph

  graph.run() // run the graph and materialize it

  /**
    * Source to 2 Sinks
    */
  val firstSink = Sink.foreach[Int](x => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink: $x"))

  val sourceTo2SinksGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[Int](2))
//    input ~> broadcast
//    broadcast.out(0) ~> firstSink
//    broadcast.out(1) ~> secondSink
    // These 3 lines above can substituted by:
    input ~> broadcast ~> firstSink // => This is called implicit port numbering
    broadcast ~> secondSink // => This is called implicit port numbering

    ClosedShape
  })

  /**
    * Fast source + slow source ==> Merge component (n-to-1) => Balance component (1-to-n) ==> Sinks (n)
    */
  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

//  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: $x"))
//  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: $x"))
  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  val balanceGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](2))

    fastSource ~> merge ~> balance ~> sink1
    slowSource ~> merge
    balance ~> sink2

    ClosedShape
  })

  balanceGraph.run()
  /*
  As a result, we'll notice that the output of the 2 sinks is balanced, and that's because we're using the Balance component.
  This component takes 2 very different sources, emitting elements at very different speeds, and it evens out the rate of
  production of the elements between these 2 sources, and splits equally in-between 2 sinks.
   */
}
