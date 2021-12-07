package gn.akka.streams.part2.graphs

import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}

// 2
object OpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  import akka.stream.Materializer.matFromSystem

  /*
  A composite source that concatenates 2 sources
    - emits all the elements from the first source
    - then all the elements from the second source
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  // step1
  val sourceGraph = Source.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    // step2: declaring components
    val concat = builder.add(Concat[Int](2))
    // step3: tying up together using ~>
    firstSource ~> concat
    secondSource ~> concat
    // step4
    SourceShape(concat.out)
  })

//  sourceGraph.to(Sink.foreach[Int](println)).run()
  // It will process the elements from 'firstSource', after that it take care of 'secondSource'

  /*
  Complex sink: Feeding 2 sinks using same source
   */
  val sink1 = Sink.foreach[Int](x => println(s"Stream 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Stream 2: $x"))

  val sinkGraph = Sink.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    // step2: declaring components
    val broadcast = builder.add(Broadcast[Int](2))
    // step3: tying up together using ~>
    broadcast ~> sink1
    broadcast ~> sink2
    // step4
    SinkShape(broadcast.in)
  })

//  firstSource.to(sinkGraph).run()

  /*
  Complex flow: composed of 2 other flow
    - 1 flow adds 1 to a number
    - 1 flow multiplies number by 10
   */

  val incrementerFlow = Flow[Int].map(_ + 1)
  val multiplierFlow = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    // step2: declaring auxiliary shapes
    val incrementerShape = builder.add(incrementerFlow)
    val multiplierShape = builder.add(multiplierFlow)
    // step3: tying up shapes together using ~>
    incrementerShape ~> multiplierShape
    // step4
    FlowShape(incrementerShape.in, multiplierShape.out)
  })

  firstSource.via(flowGraph).to(Sink.foreach[Int](println)).run()

  /**
    * Flow from a source and sink: => |flow| =>
    */
  def flowSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      val sourceShape = builder.add(source)
      val sinkShape = builder.add(sink)
      FlowShape(sinkShape.in, sourceShape.out)
    })

  // The above method 'flowSinkAndSource' already exists in Akka streams API: 'Flow.fromSinkAndSource()'
  val f = Flow.fromSinkAndSource(Sink.foreach[String](println), Source(1 to 10))
  // Such a flow is technically fine, but the problem here is that there is no connection between the components involved
  // So the danger is that, if the elements going into the Sink for example are finished, than the Source has no way of
  // stopping the stream if this flow ends up connecting various parts of your graph. That's why Akka streams has a
  // spacial version of this method called: Flow.fromSinkAndSourceCoupled, which can send termination signals and
  // backpressure signals between these 2 unconnected components (Sink and Source)
  val fCoupled = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
