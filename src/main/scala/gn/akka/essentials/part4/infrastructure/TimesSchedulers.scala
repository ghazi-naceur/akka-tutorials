package gn.akka.essentials.part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers, scala2ActorRef}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 1
object TimesSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulersTimersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  /**
  system.log.info("Scheduling reminder for simpleActor")
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }(system.dispatcher)
    */
  // We can pass the '(system.dispatcher)' as an implicit val
  /**
  implicit val executionContext: ExecutionContext = system.dispatcher
  system.log.info("Scheduling reminder for simpleActor")
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }
    */
  // or simply, import the 'system.dispatcher'
  /**
  import system.dispatcher
  system.log.info("Scheduling reminder for simpleActor")
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }

  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
    simpleActor ! "heartbeat"
  }

  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel()
  }
    */

  /*
  Don't use unstable references inside scheduled actions
  All scheduled tasks execute when the system is terminated
  Schedulers are not the best at precision and long-term planning
   */

  /**
    * Exercise: Implement a self-closing actor
    *
    * - If the actor receives a message, you have 1 second to send another message
    * - If the time window expires, the actor will stop itself
    * - If you send another message, the time window is reset
    */
  /**
  class SelfClosingActor extends Actor with ActorLogging {

    var schedule: Cancellable = createTimeoutWindow()

    override def receive: Receive = {
      case "TimeoutMessage" =>
        log.info("Stopping myself")
        context.stop(self)
      case message =>
        log.info(s"Received '$message', staying alive")
        schedule.cancel()
        schedule = createTimeoutWindow()
    }

    import system.dispatcher

    def createTimeoutWindow(): Cancellable = {
      context.system.scheduler.scheduleOnce(1 second) {
        self ! "TimeoutMessage"
      }
    }
  }

  import system.dispatcher
  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
  system.scheduler.scheduleOnce(250 millis) {
    selfClosingActor ! "ping"
  }

  system.scheduler.scheduleOnce(2 seconds) {
    system.log.info("Sending pong to the self-closing actor")
    selfClosingActor ! "pong" // will never reach, because the actor has already stopped
  }
    */
  // There is another tool provided by Akka to sent messages to self: Timer

  import system.dispatcher

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop

  class TimerBasedHeartBeatActor extends Actor with ActorLogging with Timers {

    timers.startSingleTimer(TimerKey, Start, 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping...")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      // 2 timers with the same key (TimeKey), the previous will be canceled after terminating its job
      case Reminder =>
        log.info("Actor alive")
      case Stop =>
        log.warning("Stopping...")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerBasedHeartBeatActor = system.actorOf(Props[TimerBasedHeartBeatActor], "timeActor")
  system.scheduler.scheduleOnce(5 seconds) {
    timerBasedHeartBeatActor ! Stop
  }
}
