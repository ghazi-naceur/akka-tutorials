package gn.akka.streams.part1.primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  // val graph = source.via(flow).to(sink)
  // val result = graph.run()
  // The graph won't process any data, until we can the method 'run()'
  // The 'result' returns a value, called a materialized value
  // A graph is a blueprint for a stream
  // Running a graph allocates the right resources(instantiating actors, allocating thread pools, sockets, connections)
  // Allocating the resources is transparent by default
  // Running a graph is called Materializing the graph == Materializing all components
  // Each component produces a materialized value when run
  // When the graph is run, it produces a single materialized value
  // Our job to choose which materialized value to pick. In general, the left-most materialized value is kept in the graph (source usually)
  // A component can materialize multiple times. We can reuse the same component in different graphs
  // Different runs = different materializations

  implicit val system: ActorSystem = ActorSystem("MaterializingStreams")
  import akka.stream.Materializer.matFromSystem

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
//  private val simpleMaterializedValue: NotUsed = simpleGraph.run()
  // return type is 'NotUsed', which is equivalent to 'Unit' in Scala

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int](_ + _)
  val sumFuture: Future[Int] = source.runWith(sink)
  // 'runWith' connects the 'source' to the 'sink' and executes the graph by calling the 'run' methods
  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all element is $value")
    case Failure(thr)   => println(s"The sum of elements couldn't be computed, due to: $thr")
  }
  // Choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)

  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  // or simply write:
  private val graph: RunnableGraph[Future[Done]] =
    simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  // 'Keep.left', 'Keep.both' (returns a tuple) or 'Keep.none'
  // The 'viaMat' takes the component that we want to connect to the source, and as another argument list, I can supply
  // a combination function that takes the materialized values of 'simpleSource' and 'simpleFlow' and returns a third
  // materialized value that will be the materialized value of the composite component

  graph.run().onComplete {
    case Success(_)         => println("Stream processing finished")
    case Failure(exception) => println(s"Stream processing failed with: $exception")
  }

  // Syntactic sugars
  Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // By default: source.to.(sink.reduce)(Keep.right)
  // a shorter version:
  Source(1 to 10).runReduce[Int](_ + _) // By default: source.to.(sink.reduce)(Keep.right)

  // Backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) // equivalent to source(...).to(sink(...)).run()
  // Source-to-Sink syntax: always keeps the left component materialized value (which is the Source)
  // Source-runWith-Sink syntax: always keeps the right component materialized value (which is the Sink)

  // When you have doubts about the materialized values to used, use 'viaMat' and 'toMat'.

  // both ways:
  Flow[Int].map(_ * 2).runWith(simpleSource, simpleSink)
  // This will connect the flow to 'simpleSource' and 'simpleSink'

  /**
    * Exercise:
    *
    * - return the last element out of Source (use Sink.last)
    * - compute the total word count out of a stream of sentences
    *     - map, fold, reduce
    */

  val f1 = Source(1 to 10).toMat(Sink.last)(Keep.right)
  val f2 = Source(1 to 10).runWith(Sink.last) // the same as above

  val sentenceSource = Source(List("This is a tutorial", "Netero", "Shisui", "Uchiha clan", "Konoha"))

  val wordCountSink =
    Sink.fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val g1: Future[Int] = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2: Future[Int] = sentenceSource.runWith(wordCountSink) // the same as above
  val g3: Future[Int] =
    sentenceSource.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val wordCountFlow =
    Flow[String].fold[Int](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g4: Future[Int] = sentenceSource.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()
  val g5: Future[Int] = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  // This '(Keep.left)' doesn't really matter, because in the end you'll just keep the materialized value of the sink
  // 'toMat(Sink.head)(Keep.right)'

  val g6: Future[Int] = sentenceSource.via(wordCountFlow).runWith(Sink.head)
  val g7: Future[Int] = wordCountFlow.runWith(sentenceSource, Sink.head)._2
}
