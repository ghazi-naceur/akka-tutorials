package gn.akka.streams.part4.advanced_akka_stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

// 2
object Substreams extends App {
  implicit val system: ActorSystem = ActorSystem("Substreams")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher

  // 1- Grouping a stream by a certain function
  val wordsSource = Source(List("Issac", "Netero", "is", "an", "old", "man"))
  val groups = wordsSource.groupBy(30, word => if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))
  // 'groups' is actually a stream of streams
  // 'groups' can be considered as a Source
  groups
    .to(Sink.fold(0)((count, word) => {
      val newCount = count + 1
      println(s"I just received '$word'. Count is '$newCount'.")
      newCount
    }))
//    .run()
  // Every single sub-stream will have a different materialization of this component

  // 2- Merge substreams back
  val textSource = Source(List("Isaac Netero is an old man", "An old man living somewhere", "Akka is interesting"))
  val totalCharacterCountFuture = textSource
    .groupBy(2, string => string.length % 2) // substream, as a source
    .map(_.length) // or any expensive computation here
    .mergeSubstreamsWithParallelism(2) // at this point, this is a substream
    // we can use 'mergeSubstreams', but it uses the maximum number of parallelism available in the machine
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharacterCountFuture.onComplete {
    case Success(count)     => println(s"Total characters count is '$count'")
    case Failure(exception) => println(s"Error occurred due to $exception")
  }

  // 3- Splitting a stream into substreams, when a condition is met

  val text = "Isaac Netero is an old man\n" + "An old man living somewhere\n" + "Akka is interesting\n"
  val anotherCharFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    // when the condition is met, a new substream will formed at the spot and all the following characters will be sent to
    // that substream, until the incoming character is '\n', so another substream will be created and so on ...
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharFuture.onComplete {
    case Success(count)     => println(s"Total characters count is '$count'")
    case Failure(exception) => println(s"Error occurred due to $exception")
  }

  // 4- Flattening or the equivalent of flatMap in Akka streams
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  // 'flatMapMerge' is an equivalent to merge 'mergeSubstreamsWithParallelism', and take elements from at most '2' as
  // mentioned in the above example

}
