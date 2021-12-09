package gn.akka.streams.part3.techniques_and_patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 1
object IntegratingWithActors extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithActors")
  import akka.stream.Materializer.matFromSystem

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Receiving a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Receiving a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  val numberSource = Source(1 to 10)

//   * Actor as a flow
  implicit val timeout: Timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

//  numberSource.via(actorBasedFlow).to(Sink.ignore).run()
  // or :
//  numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run()

//   * Actor as a source
  val actorPoweredSource: Source[Int, ActorRef] =
    Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  // This variable materializes to ActorRef

  val materializedActorRef: ActorRef =
    actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow number: $number"))).run()

  materializedActorRef ! 10

  // Terminating the stream
  materializedActorRef ! akka.actor.Status.Success("complete")

//  * Actor as a sink
  // - an init message
  // - an ack message to confirm the reception
  // - a complete message
  // - a function to generate a message in case the stream throws an exception

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message '$message' received")
        sender() ! StreamAck // IMPORTANT, otherwise the source will stop feeding after the 1st message, considering
      // the NO_ACK as a backpressure
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")
  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = throwable => StreamFail(throwable) // optional
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // There is another actor powered sink, which is 'Sink.actorRef()', but it is rarely used in practice and it's
  // not recommended to use it, because it doesn't provide backpressure

}
