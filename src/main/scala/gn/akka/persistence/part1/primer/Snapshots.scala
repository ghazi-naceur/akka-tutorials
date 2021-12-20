package gn.akka.persistence.part1.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

// 4
object Snapshots extends App {

  // Problem: long-lived entities take a long time to recover
  // Solution: Save checkpoints or Snapshots

  // Commands
  case class ReceivedMessage(contents: String) // message from the contact
  case class SentMessage(contents: String) // message to the contact

  // Events
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    var MAX_MESSAGES = 10 // to be hold in memory
    var currentMessageId = 0
    var commandsWithoutCheckpoint = 0
    val lastMessages = new mutable.Queue[(String, String)]()

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: '$contents'")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: '$contents'")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" =>
        log.info(s"Most recent messages: '$lastMessages'")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received message with id '$id': '$contents'")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message with id '$id': '$contents'")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered snapshot with metadata: '$metadata'")
        // put 'contents' in the 'lastMessages' queue
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot succeeded with metadata: '$metadata'")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"Saving snapshot '$metadata' failed due to: $reason")
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) {
        // dequeue the first message from the queue
        lastMessages.enqueue()
      }
      lastMessages.enqueue((sender, contents)) // latest message
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint...")
        saveSnapshot(lastMessages) // saving anything serializable to a dedicated persistent store
        // 'saveSnapshot' is asynchronous operation
        commandsWithoutCheckpoint = 0
      }
    }
  }
  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }

  val system = ActorSystem("Snapshots")
  val chat = system.actorOf(Chat.props("Isaac", "Beyond"))

//  for (i <- 1 to 10000) {
//    chat ! ReceivedMessage(s"Konaha Central $i")
//    chat ! SentMessage(s"NGL district $i")
//  }

  /*
    Persisting took quite some time to be achieved, but recovering took few seconds.
    In a production environment, we'll have millions of events so we need to quick recovery. We can use Snapshots to
    speed things up. Actually, using Snapshot there is nearly no waiting for recovery.
    Snapshot will make Akka recover only from the latest snapshot, that's why it's so fast.

    Example:
      event 1
      event 2
      event 3
      snapshot 1
      event 4
      snapshot 2
      event 5
      event 6

    When recovering, Akka will load 'snapshot 2', 'event 5' and 'event 6'
   */
  chat ! "print"

  /*
    Pattern:
      - after each persist, maybe save a snapshot
      - if you save a snapshot, you should handle the SnapshotOffer message in receiveRecover
      - (optional, but best practice) handle the SaveSnapshotSuccess and the SaveSnapshotFailure in receiveCommand
   */
}
