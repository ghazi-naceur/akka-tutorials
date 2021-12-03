package gn.akka.essentials.part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

// 4
object Mailboxes extends App {

  val system = ActorSystem("MailboxesDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
    * Case 1 - Custom priority mailbox
    * P0 - Most important
    * P1
    * P2
    * P3
    */

  // Step 1 - Mailbox definition
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _                                             => 4
      })

  // Step 2 - make it known in configuration
  // It is set in the application.conf file, under 'support-ticket-dispatcher'

  // Step 3 - attach the dispatcher to an actor
  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
//  supportTicketLogger ! "[P3] This would be nice to have"
//  supportTicketLogger ! "[P0] This must be solved now"
//  supportTicketLogger ! "[P1] Do it when you have time"
  // Mailbox will sort these message based on the partial function defined inside 'SupportTicketPriorityMailbox'
  // so the 'supportTicketLogger' actor will process P0, P1 then P3.
  /**
    * Result:
[INFO] [12/03/2021 17:59:37.224] [MailboxesDemo-support-ticket-dispatcher-6] [akka://MailboxesDemo/user/$a] [P0] This must be solved now
[INFO] [12/03/2021 17:59:37.225] [MailboxesDemo-support-ticket-dispatcher-6] [akka://MailboxesDemo/user/$a] [P1] Do it when you have time
[INFO] [12/03/2021 17:59:37.225] [MailboxesDemo-support-ticket-dispatcher-6] [akka://MailboxesDemo/user/$a] [P3] This would be nice to have
    */

  supportTicketLogger ! PoisonPill // The PoisonPill message will be postponed
//  Thread.sleep(1000) // If we add this delay, the actor will be dead, and all the following messages will be sent to DeadLetter
  supportTicketLogger ! "[P3] This would be nice to have"
  supportTicketLogger ! "[P0] This must be solved now"
  supportTicketLogger ! "[P1] Do it when you have time"
  // After which time can I send another message and be prioritized accordingly ?
  // You can't know nor configure the wait time, because when a thread is allocated to dequeue messages from this actor
  // whatever what's put in the queue in that particular order(which is ordered by the mailbox), will get handled.

  /**
    * Control-aware mailbox
    */
  // Some messages needs to be managed first regardless of what's been queued up in the mailbox.
  // We'll use the UnboundedControlAwareMailbox
  // Step 1 - mark important messages as Control messages
  case object ManagementTicket extends ControlMessage

  // Step 2 -  configure who gets the mailbox
  // Method 1 - Make the actor attach to the mailbox programmatically
  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
  controlAwareActor ! "[P0] This must be solved now"
  controlAwareActor ! "[P1] Do it when you have time"
  controlAwareActor ! ManagementTicket // Should be processed first

  /**
    * Result:
[INFO] [12/03/2021 23:27:31.617] [MailboxesDemo-akka.actor.default-dispatcher-3] [akka://MailboxesDemo/user/$b] ManagementTicket
[INFO] [12/03/2021 23:27:31.618] [MailboxesDemo-akka.actor.default-dispatcher-3] [akka://MailboxesDemo/user/$b] [P0] This must be solved now
[INFO] [12/03/2021 23:27:31.618] [MailboxesDemo-akka.actor.default-dispatcher-3] [akka://MailboxesDemo/user/$b] [P1] Do it when you have time
    */

  // Method 2 -  Attach actor to mailbox using the deployment config
  val alternativeControlAwareActor = system.actorOf(Props[SimpleActor], "alternativeControlAwareActor")
  alternativeControlAwareActor ! "[P0] This must be solved now"
  alternativeControlAwareActor ! "[P1] Do it when you have time"
  alternativeControlAwareActor ! ManagementTicket // Should be processed first

  /**
    * Result:
[INFO] [12/03/2021 23:33:05.536] [MailboxesDemo-akka.actor.default-dispatcher-4] [akka://MailboxesDemo/user/alternativeControlAwareActor] ManagementTicket
[INFO] [12/03/2021 23:33:05.537] [MailboxesDemo-akka.actor.default-dispatcher-4] [akka://MailboxesDemo/user/alternativeControlAwareActor] [P0] This must be solved now
[INFO] [12/03/2021 23:33:05.537] [MailboxesDemo-akka.actor.default-dispatcher-4] [akka://MailboxesDemo/user/alternativeControlAwareActor] [P1] Do it when you have time
    */

}
