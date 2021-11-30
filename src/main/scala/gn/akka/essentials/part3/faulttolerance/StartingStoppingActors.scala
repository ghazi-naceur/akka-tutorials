package gn.akka.essentials.part3.faulttolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}
import gn.akka.essentials.part3.faulttolerance.StartingStoppingActors.Parent.{StartChild, Stop, StopChild}

object StartingStoppingActors {

  class Parent extends Actor with ActorLogging {
    override def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child with a name '$name'")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping child with a name '$name'")
        val childOption = children.get(name)
        childOption.foreach(childRef => context.stop(childRef)) // Stopping an actor in Akka
      // 'context.stop()' is a non-blocking method, so everything happens asynchronously
      case Stop =>
        log.info("Stopping myself")
        context.stop(self) // stopping the parent and its children. It actually stops the children first, and then their
      // parent
      case message => log.info(message.toString)
    }
  }
  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop // message to tell parent to stop itself
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("StoppingActorsDemo")

    /**
      * Method 1 - Using context.stop()
      */
    import Parent._
    val parent = system.actorOf(Props[Parent], "parent")
    parent ! StartChild("child1")

    val child1 = system.actorSelection("/user/parent/child1")
    child1 ! "Hi kid!"

//    parent ! StopChild("child1")

//    for (_ <- 1 to 50) child1 ! "are you still there ?"
    // The 'child' actor will receive some of these messages before stopping, because the context.stop() method.
    // is asynchronous/non-blocking. The rest of these messages sent after the stop of the child, will be sent to
    // the DeadLetter actor.

    parent ! StartChild("child2")
    val child2 = system.actorSelection("/user/parent/child2")
    child2 ! "Hi 2nd kid!"
    // It might not be received, because the 2nd child is not ready yet, so the message will be sent to the Dead Letter

    parent ! Stop

    for (_ <- 1 to 10) parent ! "Parent, are you still there ?"
    // should not be received, because they will be received after the stopping signal

    for (i <- 1 to 100) child2 ! s"[$i] 2nd child, are you still there ?"

    /**
      * Method 2 - Using special messages
      */
    val looseActor = system.actorOf(Props[Child])
    looseActor ! "Hello loose actor"
    looseActor ! PoisonPill // invoke the stopping procedure of an actor
    looseActor ! "Are you still there ?" // will be sent to Dead Letter actor

    val abruptlyTerminatedActor = system.actorOf(Props[Child])
    abruptlyTerminatedActor ! "Hello kid actor"
    abruptlyTerminatedActor ! Kill // invoke the stopping procedure of an actor - throws 'ActorKilledException'
    abruptlyTerminatedActor ! "Are you still there ?" // will be sent to Dead Letter actor
    // 'PoisonPill' and 'Kill' are special and handled separately by Akka (you can't receive it and ignore it) , so you
    // can't handle them in your normal receive message handler

    /**
      * Method 3 - Death watch
      */
    class Watcher extends Actor with ActorLogging {
      import Parent._
      override def receive: Receive = {
        case StartChild(name) =>
          val watchedActor = context.actorOf(Props[Child], name)
          log.info(s"Starting and watching actor '$name'")
          context.watch(watchedActor) // this method registers the 'watcher' actor for the death of the 'watchedActor'
        // actor. When the 'watchedActor' dies, 'Watcher' will receive a special 'Terminated' message
        case Terminated(ref) =>
          log.info(s"THe reference that I'm watching '$ref' has been stopped")
      }
    }

    val watcher = system.actorOf(Props[Watcher], "watcher")
    watcher ! StartChild("watchedActor")
    val watchedActor = system.actorSelection("/user/watcher/watchedActor")
    Thread.sleep(500) // just to make sure that the child is created
    watchedActor ! PoisonPill

    // 'context.watch' is often used in conjunction with its opposite 'context.unwatch'. This is very useful when you
    // expect to reply from an actor and until you get a response, you register for its death watch, because it might die
    // in the meantime, and when you get the response that you want, you naturally want to unsubscribe from the actor death
    // watch, because you don't care about its life anymore
  }
}
