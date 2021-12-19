package gn.akka.persistence.part1.primer

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

import java.util.Date

//1
object PersistentActors extends App {

  /*
  Scenario: We have a business and an accountant which keep track of our invoices
   */

  // The message or the Command
  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

  // special messages
  case object Shutdown

  // The events: data structure that the Persistent Actor will send to the Persistent Store(Journal)
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    // identification of events. Best practice is to make 'persistenceId' unique by Actor
    override def persistenceId: String = "simple-accountant"

    // The normal 'receive' method
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
          When you receive a command:
            1- you create an Event to persist into the Store
            2- you persist the event, then pass in a callback that will get triggered once the Event is written
            3- you update the actor state when the event has persisted
         */
        log.info(s"Received invoice for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event) /* time gap */ { e => // placing the callback
          /*
           'persist' is asynchronous (non-blocking call)
           The callback ({ e =>...}) is asynchronous as well, and it will be executed at some point of time
           Normally, we should never access a mutable state or call methods in asynchronous callback, because this can
           break the actor encapsulation, BUT in this code, it's OK ! There is no race conditions !
           => Safe to access mutable state, because Akka Persistence guarantees that no other thread accessing the Actor
           during a callback.
           Behind the scenes, Akka Persistence is also message-based, so we can send an ACK.
           There a time gap between the original call to persist 'persist(event)' and the callback '{ e =>...}'(which
           is triggered after the event has been persisted). Meanwhile between persisting and calling back, what if
           other command are being sent at meantime: With 'persist', Akka Persistence guarantees that all messages/commands
           between persisting and handling of the callback are stashed in that time gap.
           */

          // update state
          latestInvoiceId += 1
          totalAmount += amount
          sender() ! "PersistenceACK"
          // to 'sender()', which is the one sending the 'Invoice(...)' command: Correctly identify the sender of the command
          log.info(s"Persisted '$e' as invoice number '${e.id}', for total amount: '$totalAmount'")
        }
      case InvoiceBulk(invoices) =>
        /*
          1- create events
          2- persist all event
          3- update the actor state when each event is persisted
         */
        val invoicesIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoicesIds).map { pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { e => // single event
          // the callback will be called in sequence for each event that has been persisted successfully in the Journal
          log.info(s"Persisted Single '$e' as invoice number '${e.id}', for total amount: '$totalAmount'")
          latestInvoiceId += 1
          totalAmount += e.amount
        }

      case Shutdown => context.stop(self)

      // It can act like a normal actor as well:
      case "print" =>
        log.info(s"Latest invoice id: '$latestInvoiceId', total amount: '$totalAmount'")
    }

    // The handler that will be called on recovery
    override def receiveRecover: Receive = {
      /*
        Best practise: follow the logic in the persist steps of the "receiveCommand"
       */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"Recovered invoice '#$id' for amount '$amount', total amount: '$totalAmount'")
    }

    /*
    Persistence failures:
      1- Failure to persist the event: The call to persist throws an error
      2- Rejection: the Journal implementation fails to persist the event/message
     */
    // It will be called when the 'persist(event)' fails. The actor will be stopped. Best practise is to restart the actor
    // after a while (use Backoff supervisor)
    // seqNr: Sequence number of the event from the Journal's point of view
    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Failed to persist '$event', because of '$cause'")
      super.onPersistFailure(cause, event, seqNr)
    }

    // It will be called if the Journal throws an exception while persisting the event. In this case, the actor needs
    // to be RESUMED, not stopped
    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for '$event', because of '$cause'")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")
  // 1- Persist:
//  for (i <- 1 to 10) {
//    accountant ! Invoice("Furniture store", new Date, i * 100)
//  }
  // In the first run, you'll notice in the log that the events are persisted, because the callback ('persist(event)')
  // is called. All these events were tagged with the Persistence ID (simple-accountant).
  // In the second run, you'll notice that all events with the previous Persistence ID (simple-accountant) are
  // recovered/queried (which were persisted in the previous run) from the Journal

  // 2- Persist all:
  val newInvoices = for (i <- 1 to 5) yield Invoice("New Furniture Corp", new Date, i * 1000)
  accountant ! InvoiceBulk(newInvoices.toList)
  // The 'persistAll' is called once, but the callback is called for each invoice/event

  /**
    * Never call 'persist' or 'persistAll' from Futures, otherwise you risk breaking the actor encapsulation, because the
    * actor thread is free to process messages while you're persisting. If the actor thread also calls 'persist' or 'persistAll'
    * you suddenly have 2 threads persisting threads simultaneously, and because the order of messages is not deterministic
    * you risk corrupting the actor state
    */

  // 3- Shutdown of Persistent Actor
  /*
   Shutdown using 'PoisonPill' or 'Kill' is not ideal.
   'PoisonPill' or 'Kill' are handled separately, in a separate mailbox, so you risk killing the actor, before it's
   actually done persisting.
   Best practise is to define your own "shutdown messages": A Custom message + context.shutdown(self)
   */
//  accountant ! PoisonPill
  // Messages will be sent to the Dead Letter, so invoice actually got to be persisted

  accountant ! Shutdown
  /*
  'Shutdown' is a custom message that will be put in the normal mailbox, that guarantees that this message will be
   handled after the invoices were being successfully sent and handled by the Persistent Actor
   */
  // All messages were received and the Actor was safely shutdown
}
