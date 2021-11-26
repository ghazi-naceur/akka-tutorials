package gn.akka.essentials.actor

import akka.actor.{Actor, ActorSystem, Props}

object ActorIntro {

  // With traditional objects:
  // - we store their state as data
  // - we call their methods

  // With actors:
  // - we store their state as data
  // - we send messages to them, asynchronously

  // Actors are objects we can't access directly, but only send messages to

  // Messages are asynchronous by nature:
  // - it takes time for a message to travel
  // - sending and receiving may not happen at the same time
  // Actors are uniquely identiied
  def main(args: Array[String]): Unit = {
    // part 1 - Actor system: a heavy-weight data structure that controls a number of threads under the hood that
    // allocates to run actors
    // 1 actor system per app (unless strong reasons)
    val actorSystem = ActorSystem("firstActorSystem")
    println(actorSystem.name)

    // part 2 - Create actors
    class WordCount extends Actor {
      override def receive: PartialFunction[Any, Unit] = {
        case message:
      }
    }
    actorSystem.actorOf(Props[String])
  }
}
