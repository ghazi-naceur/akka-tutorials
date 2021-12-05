package gn.akka.streams.part1.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")
//  val materializer = ActorMaterializer()(system) // akka 2.5
//  replaced with:
  import Materializer.matFromSystem // akka 2.6, for 'graph.run()'

  val source = Source(1 to 10)
  val sink = Sink.foreach[Int](println)
  val graph = source.to(sink)
  graph.run()

  val flow = Flow[Int].map(_ + 1)
  private val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  private val flowWithSink: Sink[Int, NotUsed] = flow.to(sink)

  sourceWithFlow.to(sink).run() // valid
  source.to(flowWithSink).run() // valid
  source.via(flow).to(sink).run()
  // All types can be sent over Akka Streams, if they are Serializable and not NULL
  /**
  val illegalSource = Source.single[String](null)
  illegalSource.to(Sink.foreach(println)).run()
    */
  // Use Option instead

  // Various kind of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1))
  // Do not confuse an Akka Stream with a Collection Stream

  import scala.concurrent.ExecutionContext.Implicits.global
//  val futureSource = Source.fromFuture(Future(42)) // akka 2.5
  val futureSource = Source.future(Future(42)) // akka 2.6

  // Sinks
  val ignoreSink = Sink.ignore // does not do anything
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head ans then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b) //(_ + _)

  // Flows: usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => x * 2)
  val takeFlow = Flow[Int].take(2)
  // drop, filter

  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  // Syntactic sugars
  val mapSource = Source(1 to 10).map(x => x + 2) // == Source(1 to 10).via(Flow[Int].map(x => x + 2))

  // Run streams directly
  mapSource.runForeach(println) // == mapSource.to(Sink.foreach[Int](println)).run()

  // Source, Flow, Sink ... etc, are called Operators = components

  /*
  Exercise: Create stream of strings and keep first 2 strings with length > 3 chars
   */
  private val stringSource: Source[String, NotUsed] = Source(List("Ippo", "Isaac", "Gon", "Netero", "Ging"))
  private val filterStringFlow: Flow[String, String, NotUsed] = Flow[String].filter(_.length > 3)
  private val takeStringFlow: Flow[String, String, NotUsed] = Flow[String].take(2)
  private val stringSink: Sink[String, Future[Done]] = Sink.foreach[String](println)

  stringSource.via(filterStringFlow).via(takeStringFlow).to(stringSink).run()
}
