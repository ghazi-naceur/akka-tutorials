package gn.akka.streams.part2.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

// 4
object GraphMaterializedValues extends App {
  implicit val system: ActorSystem = ActorSystem("GraphMaterializedValues")
  import akka.stream.Materializer.matFromSystem

  val wordSource = Source(List("Isaac", "Netero", "is", "an", "old", "man"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
    A composite component (sink)
    - prints out all strings which are lowercase
    - COUNTS the strings that are short (< 5 chars)

    => Need to broadcast
   */

  // step 1
  val complexWordSink =
    Sink.fromGraph(GraphDSL.create(printer, counter)((printerMatValue, counterMatValue) => counterMatValue) {
      implicit builder => (printerShape, counterShape) =>
        import GraphDSL.Implicits._
        // If you add a component inside the 'GraphDSL.create(counter)', you need to add a higher order function 'counterShape =>'

        // step 2 - SHAPES
        val broadcast = builder.add(Broadcast[String](2))
        val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
        // there is no '.isLowercase'
        val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

        // step 3 - connections
        broadcast ~> lowercaseFilter ~> printerShape
        broadcast ~> shortStringFilter ~> counterShape // 'counterShape' instead of 'counter'. Now we can return 'Future[Int]'

        // step 4 - the shape
        SinkShape(broadcast.in)
    })
  /*
    Explanation:
    GraphDSL.create(printer, counter)// We started by passing 2 components 'printer' and 'counter' to 'GraphDSL.create'.
    When that happen, Akka duplicate the shapes of these components in the composite components:
    "(printerMatValue, counterMatValue) => counterMatValue)". At some point, it will also pass the materialized value
    composition function, which picks the materialized values and composes them into 1 materialized value.
    Then, the composite component implementation will go as normal: We'll start with a mutable 'builder', then we'll
    define our logic inside, and after, we return the shape, and the builder will freeze the resulting component.
    Finally when this shape is a runnable component, the materialized value will be attached to it.
   */
  import system.dispatcher
  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
  shortStringsCountFuture.onComplete {
    case Success(count)     => println(s"The total number of short strings is: $count")
    case Failure(exception) => println(s"The count of short strings failed: $exception")
  }

  /**
    * A: input
    * B: output
    * Future[Int]: type of of the materialized value
    */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)
    // counterSink exposes the materialized value that we care about: Future[Int]
    // THe flow spits out the type B
    Flow.fromGraph(GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
      import GraphDSL.Implicits._
      // broadcast is of type B, because the flow spits out the type B
      val broadcast = builder.add(Broadcast[B](2))
      val originalFlowShape = builder.add(flow)
      // We need as well the shape of the original flow 'flow'. When you add an existing component to a composite
      // shape, 'builder.add' will duplicate/copy the 'flow' shape and add it to the composite component shape

      originalFlowShape ~> broadcast ~> counterSinkShape
      FlowShape(originalFlowShape.in, broadcast.out(1)) // 'broadcast.out(0)' is already connected to 'counterSinkShape'
    })
  }

  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore

  private val enhancedFlowCountFuture =
    simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()

  enhancedFlowCountFuture.onComplete {
    case Success(count)     => println(s"'$count' went through the enhanced flow'")
    case Failure(exception) => println(s"Error occurred: $exception")
  }
}
