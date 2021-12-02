package gn.akka.essentials.part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{
  ActorRefRoutee,
  Broadcast,
  FromConfig,
  RoundRobinGroup,
  RoundRobinPool,
  RoundRobinRoutingLogic,
  Router
}
import com.typesafe.config.ConfigFactory

object Routers {

  /**
    * 1- Manual router
    */
  class Master extends Actor {
    // Step 1 - Create routees

    // These are 5 actor routees based off Slave actor
    private val slaves = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave-$i")
      context.watch(slave)
      ActorRefRoutee(slave)
    }

    // Step 2 - Define router
    private val router = Router(RoundRobinRoutingLogic(), slaves)

    override def receive: Receive = {
      // Step 4 - Handle the termination/lifecycle of the routees
      case Terminated(reference) =>
        router.removeRoutee(reference)
        // The 'addRoutee' and 'removeRoutee' methods return a new router, so make the router a 'var' and reassign it
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router.addRoutee(newSlave)

      // Step 3 - Route the messages
      case message => router.route(message, sender())
      // 'sender()' is the requester of 'message'
      // The Slave actor will reply directly to the 'sender()'; without involving the master
    }
  }

  class Slave extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }
  def main(args: Array[String]): Unit = {

    val system = ActorSystem("RouterDemo", ConfigFactory.load().getConfig("routersDemo"))
    val master = system.actorOf(Props[Master])

    for (i <- 1 to 10) {
      master ! s"[$i] This is a message"
    }
    // Routing messages on slaves, with a RoundRobin Routing Logic
    /**
     Result:
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/$a/slave-2] [2] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/$a/slave-1] [1] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-4] [akka://RouterDemo/user/$a/slave-3] [3] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-7] [akka://RouterDemo/user/$a/slave-5] [5] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/$a/slave-2] [7] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/$a/slave-4] [4] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-4] [akka://RouterDemo/user/$a/slave-3] [8] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/$a/slave-1] [6] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-7] [akka://RouterDemo/user/$a/slave-5] [10] This is a message
[INFO] [12/02/2021 20:09:38.596] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/$a/slave-4] [9] This is a message
      */

    /*
    Supported options for routing logic:
      - round-robin
      - random
      - smallest mailbox
      - broadcast
      - scatter-gather-first
      - tail-chopping
      - consistent-hashing
     */

    /**
      * Method 2- A router with its own children
      *
      * 2-1- programmatically
      */
    val poolMaster = system.actorOf(
      RoundRobinPool(5).props(Props[Slave]),
      "" +
        "simplePoolMaster"
    )
    // 'poolMaster' will create 5 children actors of type Slave, managed by a RoundRobin logic
    for (i <- 1 to 10) {
      poolMaster ! s"[$i] Yet another message"
    }
    // This does the same thing as the Method-1 example (Manual master)
    /**
      * Result:
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/simplePoolMaster/$b] [2] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/simplePoolMaster/$a] [1] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/simplePoolMaster/$c] [3] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-7] [akka://RouterDemo/user/simplePoolMaster/$d] [4] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/simplePoolMaster/$a] [6] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-9] [akka://RouterDemo/user/simplePoolMaster/$e] [5] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-9] [akka://RouterDemo/user/simplePoolMaster/$b] [7] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-7] [akka://RouterDemo/user/simplePoolMaster/$c] [8] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/simplePoolMaster/$d] [9] Yet another message
[INFO] [12/02/2021 20:18:31.031] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/simplePoolMaster/$e] [10] Yet another message
      */

    /**
      * Method 2- A router with its own children
      *
      * 2-2- from configuration
      */
    val poolMaster2 = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
    // 'poolMaster2' must be the same name as the configuration inside the 'application.conf' file
    // It will create 5 children as Slave with a Round Robin logic
    for (i <- 1 to 10) {
      poolMaster2 ! s"[$i] Again another message"
    }

    /**
      * Result:
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/poolMaster2/$a] [1] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-2] [akka://RouterDemo/user/poolMaster2/$b] [2] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/poolMaster2/$c] [3] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/poolMaster2/$d] [4] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-12] [akka://RouterDemo/user/poolMaster2/$e] [5] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/poolMaster2/$a] [6] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/poolMaster2/$b] [7] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/poolMaster2/$c] [8] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/poolMaster2/$d] [9] Again another message
[INFO] [12/02/2021 20:25:43.955] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/poolMaster2/$e] [10] Again another message
      */

    /**
      * Method 3 - router with actors created elsewhere
      *
      * Group router
      */
    // in another part of tha application
    val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave__$i")).toList
    // need their actor paths
    val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)
    // 3.1 - programmatically
    val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())
//    for (i <- 1 to 10) {
//      groupMaster ! s"[$i] Something else"
//    }

    /**
      * Result:
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-5] [akka://RouterDemo/user/slave__1] [1] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/slave__2] [2] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/slave__3] [3] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/slave__4] [4] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/slave__5] [5] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/slave__1] [6] Something else
[INFO] [12/02/2021 20:36:11.384] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/slave__2] [7] Something else
[INFO] [12/02/2021 20:36:11.385] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/slave__3] [8] Something else
[INFO] [12/02/2021 20:36:11.385] [RouterDemo-akka.actor.default-dispatcher-11] [akka://RouterDemo/user/slave__4] [9] Something else
[INFO] [12/02/2021 20:36:11.385] [RouterDemo-akka.actor.default-dispatcher-3] [akka://RouterDemo/user/slave__5] [10] Something else
      */

    // 3.2 - From configuration
    val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")
    // 'groupMaster2' is the name provided in the configuration as well.. should be the same as the config
    for (i <- 1 to 10) {
      groupMaster2 ! s"[$i] Again something else"
    }

    /**
      * Result:
[INFO] [12/02/2021 21:13:45.033] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__1] [1] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-2] [akka://RouterDemo/user/slave__2] [2] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__3] [3] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__4] [4] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__5] [5] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__1] [6] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__2] [7] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__3] [8] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__4] [9] Again something else
[INFO] [12/02/2021 21:13:45.034] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__5] [10] Again something else
      */

    /**
      * Special messages
      */
    groupMaster2 ! Broadcast("Hello everyone!")
    // This message will be sent to every single routed actor, regardless of the routing strategy
    /**
      * Result:
[INFO] [12/02/2021 21:19:47.713] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__1] Hello everyone!
[INFO] [12/02/2021 21:19:47.713] [RouterDemo-akka.actor.default-dispatcher-2] [akka://RouterDemo/user/slave__2] Hello everyone!
[INFO] [12/02/2021 21:19:47.713] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__3] Hello everyone!
[INFO] [12/02/2021 21:19:47.713] [RouterDemo-akka.actor.default-dispatcher-2] [akka://RouterDemo/user/slave__4] Hello everyone!
[INFO] [12/02/2021 21:19:47.713] [RouterDemo-akka.actor.default-dispatcher-6] [akka://RouterDemo/user/slave__5] Hello everyone!

      */

    // 'PoisonPill' and 'Kill' are NOT routed. They are handled by their routing actor.
    // 'AddRoutee', 'RemoveRoutee', 'GetRoutee' handled only by the routing actor
  }
}
