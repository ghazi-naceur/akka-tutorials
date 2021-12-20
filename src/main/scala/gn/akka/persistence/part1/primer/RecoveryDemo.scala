package gn.akka.persistence.part1.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

// 5
object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted '$event'. Recovery is ${if (this.recoveryFinished) "" else "not"} finished")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case Event(id, contents) =>
//        if (contents.contains("123")) {
//          throw new RuntimeException("Not supported message")
//        }
        log.info(s"Recovered: '$contents'. Recovery is ${if (this.recoveryFinished) "" else "not"} finished")
        context.become(online(id + 1)) // This is useless during recovery for 'receiveRecovery', because it won't change
      // the handler 'receiveRecovery'. This method will be always used during recovery regardless of how many 'context.become'
      // you'll use. 'context.become(online(id))' is only useful on the final handler used during 'context.become' will
      // be then used to receive commands
      // => After recovery the 'normal' handler will be the result of all the stacking of context.becomes.

      case RecoveryCompleted =>
        // additional initialization can be done here
        log.info("Actor finished recovering")
    }

    override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("Failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

//    override def recovery: Recovery = Recovery(toSequenceNr = 100)
    // Recovering at most 100 messages
//    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none // displaying the recovery
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")
  /*
    1- Stashing of events
   */
  for (i <- 1 to 1000) {
    recoveryActor ! Command(s"Command $i")
  }
  // All commands sent during recovery are stashed.

  /*
    2- Failure during recovery:
   */
//  overriding the 'onRecoveryFailure' method
//  onRecoveryFailure method will be called + Actor is stopped

  /*
    3- Customizing recovery
   */
//  overriding the 'recovery' method
//  Do not persist more events after a customized recovery, especially when the customized recovery is incomplete, because
//  you can corrupt message in the meantime

  /*
    4- Recovery status or knowing when you're done recovery
   */
//  Using 'this.recoveryFinished', but it is useful.
//  Getting a signal when you're done recovering: a special message called 'RecoveryCompleted' in the 'onRecovery' method

  /*
    5- Stateless actors
   */
  // context.become will complicate matters unnecessarily for Persistent Actors when recovering
  // We can safely use 'context.become' with 'receiveCommand', because this method calls the ordinary 'receive' method,
  // so as if we're dealing with a simple Actor
  recoveryActor ! Command("Special command 1")
  recoveryActor ! Command("Special command 2")
  // The ids of these 2 special commands are '999' and '1000', so in a sense the actor is picking up where it left off,
  // by using 'context.become' inside the 'receiveRecover' handler
  // In practice, we can't really do 'context.become' with the right parameter in 'receiveRecover', because 'receiveRecover'
  // does not take any parameters, so we need to store the data for 'context.become' directly in the persistent event
  // So this logic might not be possible for all cases. In our case, fortunately, we have the parameter 'id' inside
  // the 'Event' case class, so the 'context' have the 'id'(or data) that it want to switch(become) to.
}
