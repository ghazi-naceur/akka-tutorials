package gn.akka.essentials.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import gn.akka.essentials.actor.ChildActors.CreditCard.{AttachedToAccount, CheckStatus}
import gn.akka.essentials.actor.ChildActors.Parent.{CreateChild, TellChild}

// 6
object ChildActors {

  // Actors can create other Actors
  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"Parent's path: '${self.path}' - Creating a child")
        // Create an actor
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) => childRef forward message
    }
  }
  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"Child's Path: '${self.path}' - Receiving a message: '$message'")
    }
  }

  // Bad Example !!!
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    var amount = 0
    override def receive: Receive = {
      case InitializeAmount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachedToAccount(this) // !!!
      case Deposit(funds)  => deposit(funds)
      case Withdraw(funds) => withdraw(funds)
    }

    def deposit(funds: Int): Unit = {
      println(s"${self.path} - Depositing $funds on top $amount")
      amount += funds
    }
    def withdraw(funds: Int): Unit = {
      println(s"${self.path} - Withdrawing $funds from $amount")
      amount -= funds
    }
  }
  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAmount
  }

  class CreditCard extends Actor {
    override def receive: Receive = {
      case AttachedToAccount(account) => context.become(attachedTo(account))
    }

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path}: Your message has been processed")
        //begin
        account.withdraw(1) // can be done .. no restrictions
    }
  }
  object CreditCard {
    case class AttachedToAccount(account: NaiveBankAccount)
    // This questionable, because 'NaiveBankAccount' is of type Actor. In fact, this should be substituted as:
    // case class AttachedToAccount(accountRef: ActorRef)

    case object CheckStatus
  }
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ParentChildDemo")
    val parent = system.actorOf(Props[Parent], "parentActor")
    parent ! CreateChild("childActor")
    parent ! TellChild("Hey kid")

    // Actor Hierarchies
    // parent -> child1  -> grandChild
    //        -> child2

    // Guardian Actors:
    //  - /system: System Guardian
    //  - /user: User Level Guardian (parent of actors created by 'system.actorOf' or 'context.actorOf')
    //  - /: The Root Guardian, which manages both System Guardian and User Level Guardian (highest level)

    /**
      * Actor selection
      */
    val childSelection = system.actorSelection("/user/parentActor/childActor")
    childSelection ! "I found you"
    val nonExistingChild = system.actorSelection("/user/parentActor/wrongPath")
    nonExistingChild ! "I found you" // will be sent to "dead letters"

    /**
      * Never pass mutable actor state, or the 'this' reference to child actor, because this breaks the Actor's encapsulation
      */
    import NaiveBankAccount._
    import CreditCard._
    val bankAccount = system.actorOf(Props[NaiveBankAccount], "naiveBankAccount")
    bankAccount ! InitializeAmount
    bankAccount ! Deposit(100)
    Thread.sleep(500)
    val creditCardSelection = system.actorSelection("/user/naiveBankAccount/card")
    creditCardSelection ! CheckStatus

    /**
      * Result:
      * We'll receive as a log in the end 'akka://ParentChildDemo/user/naiveBankAccount - Withdrawing 1 from 100'.
      * This withdraw is illegitimate because we're just asking for status with 'CheckStatus' message.
      * The problem here is we're calling a method 'account.withdraw(1)' when we're processing a message, and this is wrong:
      * In practise, the change of the Actor's behavior without using messages is dangerous and extremely hard to debug.
      * Calling a method directly on a message processing, bypasses all the security checks that we're be normally do in
      * its receive message handler.
      * The problem originated by declaring this message 'case class AttachedToAccount(account: NaiveBankAccount)', because
      * instead of 'ActorReference', this message contains an instance of an actual actor JVM object, wo normally we'll do:
      * 'case class AttachedToAccount(accountRef: ActorRef)'. After that in the receive method of the NaiveBankAccount, we
      * have 'creditCardRef ! AttachedToAccount(this)', which compounds the mistake by passing 'this' reference to the Child
      * Actor. When you use 'this' reference in a message, we're basically exposing yourself to method calls from other
      * Actors, which basically means method calls from other threads, that means that you're exposing yourself of to
      * concurrency issues, which we want to avoid in the first place. This is breaking the Actor encapsulation and
      * every single principal, because every single interaction with an Actor must happen through messages, never through
      * calling methods.
      * The third step of the problem is calling directly 'account.withdraw(1)' on an Actor instance. We never call methods
      * in Actors. We only send messages.
      *
      * This problem is called 'Closing over' mutable state or the 'this' reference. Scala doesn't prevent this at compile
      * time, so it's our job to make sure we keep the Actor Encapsulation.
      * = => Never close over mutable state or `this`
      *
      */
  }
}
