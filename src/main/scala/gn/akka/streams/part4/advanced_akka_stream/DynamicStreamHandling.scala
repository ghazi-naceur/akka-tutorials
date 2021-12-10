package gn.akka.streams.part4.advanced_akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{FlowShape, Graph, KillSwitches, UniqueKillSwitch}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 1
object DynamicStreamHandling extends App {
  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  import akka.stream.Materializer.matFromSystem

  // 1- Kill Switch: used to stop stream in purpose
  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int] // Flow
  // Mat val: UniqueKillSwitch
  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val simpleSink = Sink.ignore

  /**
  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    //    .toMat(simpleSink)(Keep.left) // or simply:
    .to(simpleSink)
    .run()

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown() // will shutdown the stream
  }
    */
  /**
  // 1- Shared Kill Switch: used to stop multiple streams
  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll") // to kill multiple streams at once
  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  // 'sharedKillSwitch.flow' can be materialized in multiple streams
  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown() // will kill the 2 above streams linked to 'counter' and 'anotherCounter'
  }
    */
  /**
  // 3- MergeHub
  val dynamicMerge = MergeHub.source[Int] // Mat Val of 'source[Int]' is 'Sink[Int, NotUsed]'
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run() // source 1 to 'materializedSink'
  Source(1 to 10).runWith(materializedSink) // source 2 to 'materializedSink'
  counter.runWith(materializedSink) // source 3 to 'materializedSink'
  // => 3 sources linked to the same Sink 'materializedSink'
  // This is how, dynamically at runtime, you can add virtual FanInInputs to the same consumer
  // The advantage of this technique is that it can be programmed to run while the stream active, unlike the GraphDSL
  // so we can use this sink anytime we like
  // The same goes for the opposite component 'Broadcast':
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)
  // Broadcast materializes in Source, because it has outputs, and Merge materializes in Sink, because it has inputs
  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))
    */
  /**
    * Combining a mergeHub and a broadcastHub
    *
    * A publisher-subscriber component, so you can add dynamically sources and sinks to this component, and every single
    * element produced by every single source will be known by every single subscriber
    */
  val merge = MergeHub.source[String]
  val bcast = BroadcastHub.sink[String]
  val (sinkPort, sourcePort) = merge.toMat(bcast)(Keep.both).run()
  sourcePort.runWith(Sink.foreach[String](elem => println(s"I received: $elem")))
  sourcePort.map(_.length).runWith(Sink.foreach[Int](n => println(s"I received a number: $n")))
  // => 2 Sinks attached to 'sourcePort'

  Source(List("Akka", "Learning", "course")).runWith(sinkPort)
  Source(List("Isaac", "Netero", "hxh")).runWith(sinkPort)
  Source.single("streams").runWith(sinkPort)
  // => 3 sources attached to 'sinkPort'
  // If we try to run these 3 previous sources (lists of strings), we'll see they be printed to both 'sourcePort'
  // subscribers
  /*
  Result:
      I received: streams
      I received: Akka
      I received: Isaac
      I received: Netero
      I received: Learning
      I received: course
      I received: hxh
      I received a number: 7
      I received a number: 4
      I received a number: 5
      I received a number: 6
      I received a number: 8
      I received a number: 6
      I received a number: 3

   */
}
