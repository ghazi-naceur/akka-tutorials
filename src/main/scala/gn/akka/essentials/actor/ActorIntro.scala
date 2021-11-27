package gn.akka.essentials.actor

import akka.actor.{Actor, ActorSystem, Props}

// 1
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
    class WordCountActor extends Actor {
      // internal data
      var totalWords = 0

      // behavior
      // 'PartialFunction[Any, Unit]', aliased as 'Receive'
      override def receive: Receive = {
        case message: String =>
          println(s"Received message: '$message'")
          totalWords += message.split(" ").length
        case msg => println(s"Weird message: '$msg'")
      }
    }

    // part 3 - instantiate actor
    val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
    val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

    // part 4 - communicate or send message asynchronously
    wordCounter ! "Learning Akka!"
    anotherWordCounter ! "Learning Akka again!"

    // How to instantiate an Actor argument ?
    // solution 1 (discouraged): instantiating with 'new' inside 'Props'
    class Person(name: String) extends Actor {
      override def receive: Receive = {
        case "hi" => println(s"Hi $name !")
        case _ =>
      }
    }

    val person = actorSystem.actorOf(Props(new Person("Isaac")))
    person ! "hi"

    // solution 2 (best practise): Using a companion object that returns a Props object ==>
    // We won't create a new instance of Person ourselves, but the factory method will do that for us
    object Person {
      def props(name: String): Props = Props(new Person(name))
    }

    val anotherPerson = actorSystem.actorOf(Person.props("Shisui"))
    anotherPerson ! "hi"
  }
}
