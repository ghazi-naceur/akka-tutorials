package gn.akka.streams.part4.advanced_akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Outlet, Shape}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 3
object CustomGraphShapes extends App {
  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  import akka.stream.Materializer.matFromSystem

  // Balance 2x3 shape
  case class Balance2x3(in0: Inlet[Int], in1: Inlet[Int], out0: Outlet[Int], out1: Outlet[Int], out2: Outlet[Int])
      extends Shape {
    override def inlets: immutable.Seq[Inlet[_]] = List(in0, in1)

    override def outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape =
      Balance2x3(in0.carbonCopy(), in1.carbonCopy(), out0.carbonCopy(), out1.carbonCopy(), out2.carbonCopy())
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(merge.in(0), merge.in(1), balance.out(0), balance.out(1), balance.out(2))
  }

  val balance2x3Graph = RunnableGraph
    .fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1 second)

      def createSink(index: Int): Sink[Int, Future[Int]] =
        Sink.fold(0)((count: Int, elem: Int) => {
          println(s"[sink-$index] Received '$elem', current count is '$count'")
          count + 1
        })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1
      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3

      ClosedShape
    })
//    .run()

  /*
  Result: All sinks are treated equally, despite the fact that we have 2 sources running with a different pace

  [sink-1] Received '1', current count is '0'
  [sink-2] Received '1', current count is '0'
  [sink-3] Received '2', current count is '0'
  [sink-1] Received '3', current count is '1'
  [sink-2] Received '2', current count is '1'
  [sink-3] Received '4', current count is '1'
  [sink-1] Received '3', current count is '2'
  [sink-2] Received '5', current count is '2'
  [sink-3] Received '6', current count is '2'
   */

  /**
    * Exercise: Generalizing the component Balance2x3 => BalanceMxN and letting it handle other types besides Int
    */
  case class BalanceMxN[T](override val inlets: List[Inlet[T]], override val outlets: List[Outlet[T]]) extends Shape {
    override def deepCopy(): Shape = BalanceMxN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMxN {
    def apply[T](inputCount: Int, outputCount: Int): Graph[BalanceMxN[T], NotUsed] = {
      // Mentioning the return type above for the 'apply' method is mandatory for the app to work
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val merge = builder.add(Merge[T](inputCount))
        val balance = builder.add(Balance[T](outputCount))

        merge ~> balance

        BalanceMxN(merge.inlets.toList, balance.outlets.toList)
      }
    }
  }

  val balanceMxNGraph = RunnableGraph
    .fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1 second)

      def createSink(index: Int): Sink[Int, Future[Int]] =
        Sink.fold(0)((count: Int, elem: Int) => {
          println(s"[sink-$index] Received '$elem', current count is '$count'")
          count + 1
        })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balanceMxN = builder.add(BalanceMxN[Int](2, 3))

      slowSource ~> balanceMxN.inlets(0)
      fastSource ~> balanceMxN.inlets(1)
      balanceMxN.outlets(0) ~> sink1
      balanceMxN.outlets(1) ~> sink2
      balanceMxN.outlets(2) ~> sink3

      ClosedShape
    })
    .run()
  // Result: A similar result as the previous example
}
