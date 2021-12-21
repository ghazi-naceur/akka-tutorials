package gn.akka.persistence.part1.primer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

// 6
object PersistAsyncDemo extends App {
  /*
    persistAsync:
      - High throughtput use cases
      - relaxed event ordering guarantees
   */

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor(eventAggregator))
  }
  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {

    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing '$contents'"
        persistAsync(Event(contents)) { e =>
          eventAggregator ! e
        }
        // calling persist again
        // some actual computation
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) { e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(s"Recovered: '$message'")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Aggregating '$message'")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "streamProcessor")

  streamProcessor ! Command("command 1")
  streamProcessor ! Command("command 2")

  /*
  Result:
   1- Persisting using 'persist':
     Aggregating 'Processing 'command 1''
     Aggregating 'Event(command 1)'
     Aggregating 'Event(command 1_processed)'

     Aggregating 'Processing 'command 2''
     Aggregating 'Event(command 2)'
     Aggregating 'Event(command 2_processed)'

  => Everything related to "Command 1" is processed before "Command 2"
      'persist' is asynchronous operation, but ordering is guaranteed


   2- Persisting using 'persistAsync':
      Aggregating 'Processing 'command 1''
      Aggregating 'Processing 'command 2''
      Aggregating 'Event(command 1)'
      Aggregating 'Event(command 1_processed)'
      Aggregating 'Event(command 2)'
      Aggregating 'Event(command 2_processed)'

  => persistAsync operation are not ordered, because they're performed asynchronously

  The difference between 'persist' and 'persistAsync' is the time gap between persist method and the callback.
  In fact, while 'persist', the incoming messages will be stashed in the time gap. In other hand, using 'persistAsync'
  the incoming messages were not stashed. In real world, the time to persistAsync is small compared to the time gap.
  Because of the time gaps are very large, it is very likely, that "Command 2" ends up in one of these 2 time gaps, and
  when it does, it will not be stashed and it will be processed, which explains processing "Command 1" and "Command 2"
  right after.
  So 'persistAsync' means that you can receive messages in the meantime in-between the time gaps, so the 'persistAsync'
  and the callback are not atomic anymore. So 'persistAsync' relaxes the persistence guarantees. In all cases, "Command 1"
  will be executed before "Command 2", and as a consequence the callback of 1st command will be executed before the 2nd
  command, that why 'Event(command 1)' is always processed before 'Event(command 1_processed)'. At the same time, because
  you're sending 2 commands in sequence to the Stream Processor Actor, that means that the 1st 'persistAsync' operation
  will be executed before the 2nd one, which means that you'll always see the group for "Command 1" being handled before
  the group for "Command 2", that why 'Event(command 1)' and 'Event(command 1_processed)' are performed before 'Event(command 2)'
  'Event(command 2_processed)'
   */

  /*
    persist vs persistAsync:
      - 'persistAsync' is more performant, because with 'persist' you'll have to wait until the entire command is processed
      (2 async persists), before processing any other command, so during these time gaps, any incoming commands will be
      stashed. In other terms, all operations including the time gaps must be passed, before handling any another command.
      => This is very useful in high throughput environments
      - 'persistAsync' is bad when you need to the ordering of the events, especially when dealing with mutable data.
      Persisting asynchronously can lead to a inconsistent state
   */
}
