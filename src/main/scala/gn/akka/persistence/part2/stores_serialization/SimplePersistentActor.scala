package gn.akka.persistence.part2.stores_serialization

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

class SimplePersistentActor extends PersistentActor with ActorLogging {

  var nMessages = 0

  override def persistenceId: String = "simple-persistent-actor"

  override def receiveCommand: Receive = {
    case "print"                              => log.info(s"I have persisted '$nMessages' messages")
    case "snap"                               => saveSnapshot(nMessages)
    case SaveSnapshotSuccess(metadata)        => log.info(s"Saving snapshot was successful, $metadata")
    case SaveSnapshotFailure(metadata, cause) => log.info(s"Saving snapshot failed due to: $cause")
    case message =>
      persist(message) { _ =>
        log.info(s"Persisting: '$message'")
        nMessages += 1
      }
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted => log.info("Recovery done")
    case SnapshotOffer(metadata, payload: Int) =>
      log.info(s"Recovered snapshot: '$payload'")
      nMessages = payload
    case message =>
      log.info(s"Recovered: '$message'")
      nMessages += 1
  }
}
