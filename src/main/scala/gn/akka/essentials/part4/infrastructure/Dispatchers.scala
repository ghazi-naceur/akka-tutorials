package gn.akka.essentials.part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

// 3
object Dispatchers extends App {

  // A dispatcher controls how many message are being sent and handled

  class Counter extends Actor with ActorLogging {
    var count = 0
    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"message: $message - count: $count")
    }
  }

  val system = ActorSystem("DispatcherDemo") // , ConfigFactory.load().getConfig("dispatcherDemo"))

  val simpleCounterActor = system.actorOf(Props[Counter].withDispatcher("my-dispatcher"))

  // Method 1 - Attaching a dispatcher programmatically
  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")

  val random = new Random()
  for (i <- 1 to 1000) {
    actors(random.nextInt(10)) ! i
  }
  // We'll see each 3 first actors, having exactly 30 messages each. after that the dispatcher/thread
  // will move to other 3 actors
  // that will receive as well 30 messages, because in the configuration, we defined the 'fixed-pool-size' with 3 actors
  // and 'throughput' with 30 messages.
  // If we set 'fixed-pool-size' to 1, the app will single-threaded, so the first 30 messages will be received by a single
  // actor, after that the thread will move to another actor that will receive in his turn 30 consecutive messages as well
  // and so on and so on

  // Method 2 - Attaching a dispatcher by configuration
  val dispatcher2 = system.actorOf(Props[Counter], "dispatcher2") // 'dispatcher2' same name as in config

  // Dispatchers implement the ExecutionContext trait (we're already 'import context.dispatcher' in the Timers/Schedulers lecture)

  class DBActor extends Actor with ActorLogging {
    implicit val executionContext: ExecutionContext = context.dispatcher
    override def receive: Receive = {
      // This future will run on the execution context dispatcher
      case message =>
        Future {
          // wait on a resource
          Thread.sleep(5000)
          log.info(s"success: $message")
        }
    }
  }
  /*
Running future inside of an actor is usually discouraged, but in this case there is a problem with the blocking call inside
future, because if you're running a future with a long or a blocking call ('Thread.sleep(5000)'), we may starve the
'context.dispatcher' running threads, which are usually running messages.
   */
  val dbActor = system.actorOf(Props[DBActor])
//  dbActor ! "This is a message"

  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"message $i"
    dbActor ! message
    nonBlockingActor ! message
  }
  // There is hicap every 5 seconds caused by the thread blocking inside the future, which is starving the actors from
  // receiving messages ==> very discourage generally. If you're obliged to do it, use a special dispatcher for this task
  // and replace the execution context with 'context.system.dispatchers.lookup("my-dispatcher")' and then you can use a
  // dedicated dispatcher to operate your futures == This is a potential solution nb 1.
  // There is second solution, which consists of using a Router
}
