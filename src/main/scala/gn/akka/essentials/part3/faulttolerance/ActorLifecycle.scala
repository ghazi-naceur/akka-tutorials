package gn.akka.essentials.part3.faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

// 3
object ActorLifecycle {

  object StartChild

  class LifecycleActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I have stopped")

    override def receive: Receive = {
      case StartChild => context.actorOf(Props[LifecycleActor], "child")
    }
  }

  object Fail
  object Check
  object FailChild
  object CheckChild

  class Parent extends Actor {
    private val child = context.actorOf(Props[Child], "supervisedChild")
    override def receive: Receive = {
      case FailChild  => child ! Fail
      case CheckChild => child ! Check
    }
  }
  class Child extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("Supervised child starting")
    override def postStop(): Unit = log.info("Supervised child stopped")
    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.info(s"Supervised child restarting, because of ${reason.getMessage}")
    override def postRestart(reason: Throwable): Unit = log.info("Supervised child restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("Child will fail now")
        throw new RuntimeException("Child failed")
      case Check =>
        log.info("Child will be checked")

    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("LifecycleDemo")
    val parent = system.actorOf(Props[LifecycleActor], "parent")
    parent ! StartChild
    parent ! PoisonPill

    /**
      * Result:
[INFO] [12/01/2021 11:54:45.566] [LifecycleDemo-akka.actor.default-dispatcher-3] [akka://LifecycleDemo/user/supervisor/supervisedChild] Supervised child starting
[WARN] [12/01/2021 11:54:45.567] [LifecycleDemo-akka.actor.default-dispatcher-3] [akka://LifecycleDemo/user/supervisor/supervisedChild] Child will fail now
[ERROR] [12/01/2021 11:54:45.570] [LifecycleDemo-akka.actor.default-dispatcher-2] [akka://LifecycleDemo/user/supervisor/supervisedChild] Child failed
java.lang.RuntimeException: Child failed
	at gn.akka.essentials.part3.faulttolerance.ActorLifecycle$Child$$anonfun$receive$3.applyOrElse(ActorLifecycle.scala:42)
	at akka.actor.Actor.aroundReceive(Actor.scala:517)
	at akka.actor.Actor.aroundReceive$(Actor.scala:515)
	at gn.akka.essentials.part3.faulttolerance.ActorLifecycle$Child.aroundReceive(ActorLifecycle.scala:32)
	at akka.actor.ActorCell.receiveMessage(ActorCell.scala:588)
	at akka.actor.ActorCell.invoke(ActorCell.scala:557)
	at akka.dispatch.Mailbox.processMailbox(Mailbox.scala:258)
	at akka.dispatch.Mailbox.run(Mailbox.scala:225)
	at akka.dispatch.Mailbox.exec(Mailbox.scala:235)
	at akka.dispatch.forkjoin.ForkJoinTask.doExec(ForkJoinTask.java:260)
	at akka.dispatch.forkjoin.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1339)
	at akka.dispatch.forkjoin.ForkJoinPool.runWorker(ForkJoinPool.java:1979)
	at akka.dispatch.forkjoin.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:107)

[INFO] [12/01/2021 11:54:45.571] [LifecycleDemo-akka.actor.default-dispatcher-4] [akka://LifecycleDemo/user/supervisor/supervisedChild] Supervised child restarting, because of Child failed
[INFO] [12/01/2021 11:54:45.571] [LifecycleDemo-akka.actor.default-dispatcher-2] [akka://LifecycleDemo/user/parent/child] I have stopped
[INFO] [12/01/2021 11:54:45.572] [LifecycleDemo-akka.actor.default-dispatcher-4] [akka://LifecycleDemo/user/supervisor/supervisedChild] Supervised child restarted
[INFO] [12/01/2021 11:54:45.572] [LifecycleDemo-akka.actor.default-dispatcher-4] [akka://LifecycleDemo/user/supervisor/supervisedChild] Child will be checked
[INFO] [12/01/2021 11:54:45.572] [LifecycleDemo-akka.actor.default-dispatcher-5] [akka://LifecycleDemo/user/parent] I have stopped
      */

    // The supervised child was restarted because of the raised RuntimeException, because both of "preRestart" and "postRestart"
    // are called. After restarting, the supervised child may process messages (if his parent is still alive). So even if the
    // supervised child raised an exception, it was still restarted and was able to process more messages (Check message).
    // This is the default Supervision Strategy, which consists of if an actor throw an exception when processing a message,
    // (Fail message that caused the exception) this message will be removed from the queue and not put in the mailbox again
    // and the actor is restarted, which means that the mailbox is untouched
  }
}
