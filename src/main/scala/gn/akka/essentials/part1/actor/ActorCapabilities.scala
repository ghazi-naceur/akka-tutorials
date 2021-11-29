package gn.akka.essentials.part1.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

// 2
object ActorCapabilities {
  def main(args: Array[String]): Unit = {
    class SimpleActor extends Actor {
      // Each Actor has a member called 'context', which is a complex data structure that has references to information
      // regarding the environment this Actor runs in
      // 'context.self' or simply 'self' is the actor reference of this actor (equivalent to 'this' in OOP)
      println(context.self)
      println(self)
      // Actor[akka://actorCapabilitiesDemo/user/simpleActor#1825076658] =
      // Actor path (akka://actorCapabilitiesDemo/user/simpleActor) + Actor Unique ID (1825076658)
      override def receive: Receive = {
        case "Hi!" => context.sender() ! "Hello!"
        // 'context.sender()' and simply 'sender()' returns an ActorRef, which is the actor reference that last send me a
        // message, which we can use to send a message back to the sender
        // Simply, this is replying to a message
        case message: String         => println(s"Receiving a string '$message'")
        case number: Int             => println(s"Receiving a number '$number'")
        case SpecialMessage(content) => println(s"Receiving something special '$content'")
        case SendMessageToYourself(content) =>
          println(s"Receiving content '$content'")
          self ! content // not useful, but the message will be send twice, but in the second time (self ! content)
        // it will receive a String, not the case class SendMessageToYourself, because in the second attempt, we're sending
        // the 'content', which is a string, so the String pattern matching case will be triggered
        case SayHiTo(reference) => reference ! "Hi!"
        case WirelessPhoneMessage(content, reference) =>
          reference forward content // Keeping the original sender of the WPM
      }
    }

    val system = ActorSystem("actorCapabilitiesDemo")

    val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
    // 1- Messages can be of any type:
    //   - Messages must be IMMUTABLE
    //   - Messages must be SERIALIZABLE (the JVM can transform it into a bytestream and send it to another JVM, whether it's
    //    in the same machine or over the network)
    simpleActor ! "Hello Actor"
    simpleActor ! 42
    // In practise, we use 'case class' and 'case object' as messages
    case class SpecialMessage(content: String)
    simpleActor ! SpecialMessage("some special content")

    // 2- Actors have information about their context and about themselves
    case class SendMessageToYourself(content: String)
    simpleActor ! SendMessageToYourself("This is Actor")

    // 3- Actor can reply to messages
    val isaac = system.actorOf(Props[SimpleActor], "isaac")
    val shisui = system.actorOf(Props[SimpleActor], "shisui")

    case class SayHiTo(ref: ActorRef)
    isaac ! SayHiTo(shisui) // Isaac send 'Hi!', and Shisui replies 'Hello!'

    //4- Dead letters
    isaac ! "Hi!" // Isaac will try to send a message which will invoke 'context.sender()' replying back. So we need to
    // know the last send. In this case, the last sender will be 'noSender', which is 'null' (see '!' implementation).
    // This will cause, that the message from Isaac to 'deadLetters' won't be delivered. 'deadLetters' is a fake Actor
    // inside Akka, which takes care to receive the message that won't be sent to anyone. Similar to a garbage pool of
    // messages

    // 5- Forwarding messages
    // Forwarding a message from Isaac to Shisui (passing by other persons as well), by keeping the original sender
    case class WirelessPhoneMessage(content: String, ref: ActorRef)
    isaac ! WirelessPhoneMessage("Hi there !", shisui) // The original sender is 'noSender'

    /**
      * Every Actor type derives from a trait called 'Actor' with an abstract method called 'receive', and this returns
      * a message handler object, which is retrieved by Akka, when an Actor receives a message.
      * This handler is invoked when the Actor actually processes a message on one of the thread managed by the Actor System
      */
  }
}
