package gn.akka.streams.part2.graphs

import akka.actor.{Actor, ActorSystem}
import akka.stream.{ClosedShape, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}

// 6
object GraphCycles extends App {
  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  import akka.stream.Materializer.matFromSystem

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape // closing a cycle

    ClosedShape
  }
//  RunnableGraph.fromGraph(accelerator).run()

  // Result is only showing: 'Accelerating 1' ==> Backpressure
  // This causes a cycle deadlock

  /*
    Solution 1:  MergePreferred can break this deadlock
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    // 1: is one additional port, because 'MergePreferred' has already a preferred port
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape // closing a cycle

    ClosedShape
  }
//  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
  Solution 2:  Buffer
   */
  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape // closing a cycle

    ClosedShape
  }
//  RunnableGraph.fromGraph(bufferedRepeater).run()
  // Result: There are some numbers repeating several times, but the numbers are increasing

  /*
  Cycles can cause deadlocking
    - Add bounds to the number of elements

   The challenge that you need to face when adding cycles is: Boundedness vs Liveness
   */

  /**
    * Exercise: Craete a fan-in shape
    *  - 2 inputs which will be fed with exactly 1 number each (1 and 1)
    *  - output will emit an infinite Fibonacci sequence, based off these 2 numbers
    *
    *  1,2,3,5,8,13,.....etc
    *
    *  Hint: use ZipWith and Cycles, MergePreferred
    */

  val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val zip = builder.add(Zip[BigInt, BigInt])
    val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fiboLogic = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      val last = pair._1
      val previous = pair._2
      Thread.sleep(100)
      // We need to add a thread sleep otherwise the list of the fibonacci numbers will grow fast and we won't have the
      // chance to see the actual progression
      (last + previous, last) // 'last + previous' is the next biggest fibonacci number
    })
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    zip.out ~> mergePreferred ~> fiboLogic ~> broadcast ~> extractLast
    mergePreferred.preferred <~ broadcast

    UniformFanInShape(extractLast.out, zip.in0, zip.in1) // because it takes 2 inputs from the same type
  }
  val fiboGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val source1 = builder.add(Source.single[BigInt](1))
    val source2 = builder.add(Source.single[BigInt](1))
    val sink = builder.add(Sink.foreach[BigInt](println))
    val fibo = builder.add(fibonacciGenerator)

    source1 ~> fibo.in(0)
    source2 ~> fibo.in(1)
    fibo.out ~> sink

    ClosedShape
  })

  fiboGraph.run()
}
