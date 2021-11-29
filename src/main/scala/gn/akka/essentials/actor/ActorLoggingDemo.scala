package gn.akka.essentials.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}

// 8
object ActorLoggingDemo {

  // 1- Explicit Logging
  class SimpleActorWithExplicitLogger extends Actor {
    val logger: LoggingAdapter = Logging(context.system, this)
    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  // 2- Actor Logging
  class SimpleActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case (a, b)  => log.info(s"Two things: {} and {}", a, b) // interpolation
      case message => log.info(message.toString)
    }
  }

  def main(args: Array[String]): Unit = {
    val system: ActorSystem = ActorSystem("LoggingDemo")
    val actor1: ActorRef = system.actorOf(Props[SimpleActorWithExplicitLogger])
    actor1 ! "This is a message will be logged by LoggingAdapter"

    val actor2: ActorRef = system.actorOf(Props[SimpleActorWithLogging])
    actor2 ! "This is a message will be logged by ActorLogging"
    actor2 ! (42, 56)

    // Logging is asynchronous
    // Akka logging is dome with actors
    // Logging doesn't depend on a particular logger implementation. Ypi can change the logger to slf4j, if you want
  }
}
