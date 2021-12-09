package gn.akka.streams.part3.techniques_and_patterns

import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

// 4
object FaultTolerance extends App {
  implicit val system: ActorSystem = ActorSystem("FaultTolerance")
  import akka.stream.Materializer.matFromSystem

  // 1- Logging
  val faultySource = Source(1 to 10).map(n => if (n == 6) throw new RuntimeException else n)
  faultySource
    .log("trackingElements")
    .to(Sink.ignore)
//    .run()
  // Result: The stream has crashed !

  // Make sure that the loglevel is DEBUG, in order to see the result

  // 2- Gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")
    .to(Sink.ignore)
//    .run()
  // Result: The stream didn't crash

  // 3- Recover with another stream
  faultySource
    .recoverWithRetries(3, { case _: RuntimeException => Source(90 to 99) })
    .log("recoverWithRetries")
    .to(Sink.ignore)
//    .run()
  // Result: The second began, when the first source failed

  // 4- Backoff supervision
  val restartSource = {
    RestartSource
      .onFailuresWithBackoff(minBackoff = 1 second, maxBackoff = 30 seconds, randomFactor = 0.2)(() => {
        // Returning a source which can fail randomly
        val randomNumber = new Random().nextInt(20)
        Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException else elem)
      })
      .log("restartBackoff")
      .to(Sink.ignore)
//      .run()
    // This prevents if multiple components try to restart at the same time, if some Source failed, because if they do
    // restart at the same time again, probably they will bring the source down again
    // The generator function ((() => ...)) will be called with every attempt, and it needs to return another Source.
    // In fact the generator function will be called first, and when the restartSource will be plugged to whatever source
    // it has, if the value from the generator function fails, it will be swapt and the Backoff supervision will kick in
    // after 1s as mentioned in the code. In other terms, the generator function will be called again after 1s and the
    // new result will be plugged in the consumer. If that fails as well, then the Backoff supervision interval will
    // double, so the next time the generator function will be called is after 2s, and then 4s, 8s ... up to 30s.
  }

  // 5- Supervision strategy
  val numbers = Source(1 to 20)
    .map(n => if (n == 8) throw new RuntimeException("it's 8") else n)
    .log("supervision")
  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    // resume: skips the faulty element
    // stop: stop the stream
    // restart: resume + clears the internal state
    case _: RuntimeException => Resume
    case _                   => Stop
  })
  supervisedNumbers
    .to(Sink.ignore)
    .run()
  // Result: number 8 will be skipped
}
