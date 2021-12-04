package gn.akka.essentials.part5.patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo {

  /*
  Stash put messages aside for later
  When the time is right, prepend then to the mailbox and process them
   */

  /**
    * Exercise:
    * ResourceActor has access to a resource:
    *  - open => it can receive read/write requests to the resource
    *  - otherwise, it will postpone all read and write requests until the state is open
    *
    *  ResourceActor is closed initially
    *    - Open => switch to opened state
    *    - Read and Write messages are postponed
    *
    *  ResourceActor is open
    *    - Read, Write are handled
    *    - Close => switch to closed state
    *
    *  Imagining these sequence of messages:
    *  [Open, Read, Read, Write]
    *    The behavior of this ResourceActor will be:
    *      - switch to the opened state
    *      - read the data
    *      - read the data
    *      - write the data
    *  After the ResourceActor receives:
    *  [Read, Open, Write]
    *    The behavior of this ResourceActor will be:
    *      - Stash Read => Postpone Read
    *          Stash queue: [Read]
    *      - Open => switch to opened state
    *          Mailbox: [Read, Write]
    *      - read and write are handled
    */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step1- mixin the stash trait
  class ResourceActor extends Actor with ActorLogging with Stash {
    var interData = ""
    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        unstashAll() // step3- remove from stash queue and prepend messages in the mailbox, when actor is in opened state
        context.become(opened)
      case message =>
        log.info(s"Stashing '$message', because the resource actor is in closed state")
        stash() // step2- put messages in stash queue when actor is in closed state
    }

    def opened: Receive = {
      case Read =>
        log.info(s"I've read the inter data: $interData")
      case Write(data) =>
        log.info("I'm writing data")
        interData = data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing '$message', because the resource actor is in opened state")
        stash()
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("StashDemo")
    val resourceActor = system.actorOf(Props[ResourceActor])

//    resourceActor ! Write("This is new data")
//    resourceActor ! Read
//    resourceActor ! Open

    /**
      * Result:
[INFO] [12/04/2021 09:47:28.305] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Stashing 'Write(This is new data)', because the resource actor is in closed state
[INFO] [12/04/2021 09:47:28.305] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Stashing 'Read', because the resource actor is in closed state
[INFO] [12/04/2021 09:47:28.305] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Opening resource
[INFO] [12/04/2021 09:47:28.307] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] I'm writing data
[INFO] [12/04/2021 09:47:28.307] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] I've read the inter data: This is new data
      */

    resourceActor ! Read // stashed
    resourceActor ! Open // switch to opened; I've read ""
    resourceActor ! Open // stashed
    resourceActor ! Write("This is new data") // I'm writing ...
    resourceActor ! Close // switch to close; unstashAll (stash contains open) => switch to opened
    resourceActor ! Read // I've read interData ...

    /**
      * Result:
[INFO] [12/04/2021 09:50:09.819] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Stashing 'Read', because the resource actor is in closed state
[INFO] [12/04/2021 09:50:09.819] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Opening resource
[INFO] [12/04/2021 09:50:09.820] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] I've read the inter data:
[INFO] [12/04/2021 09:50:09.820] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Stashing 'Open', because the resource actor is in opened state
[INFO] [12/04/2021 09:50:09.821] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] I'm writing data
[INFO] [12/04/2021 09:50:09.821] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Closing resource
[INFO] [12/04/2021 09:50:09.821] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] Opening resource
[INFO] [12/04/2021 09:50:09.821] [StashDemo-akka.actor.default-dispatcher-5] [akka://StashDemo/user/$a] I've read the inter data: This is new data
      */

    /*
    Things to be careful about:
      - potential memory bounds on Stash
      - potential mailbox bounds when unstashing
      - no stashing twice
      - the 'Stash' trait overrides 'preStart' so must be mixed-in last
     */
  }
}
