package gn.akka.essentials.part1.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import gn.akka.essentials.part1.actor.ActorExercises.BankAccount.{Deposit, Statement, Withdraw}
import gn.akka.essentials.part1.actor.ActorExercises.Person.Revenue
// 3
object ActorExercises {

  class CounterActor extends Actor {
    import CounterActor._
    var total = 0
    override def receive: Receive = {
      case Increment => total += 1
      case Decrement => total -= 1
      case Print     => println(total)
    }
  }
  // The domain of the counter: contains the supported messages for the CounterActor
  object CounterActor {
    case object Increment
    case object Decrement
    case object Print
  }

  class BankAccount extends Actor {
    import BankAccount._
    var balance = 0
    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount <= 0) sender() ! TransactionFailure("Invalid deposit amount")
        else {
          balance += amount
          sender() ! TransactionSuccess(s"Successfully deposited $amount")
        }

      case Withdraw(amount) =>
        if (amount <= 0) sender() ! TransactionFailure("Invalid withdraw amount")
        else if (amount > balance) sender() ! TransactionFailure("Insufficient balance")
        else {
          balance -= amount
          sender() ! TransactionSuccess(s"Successfully withdrew $amount")
        }

      case Statement => println(balance)
    }
  }
  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }

  class Person extends Actor {
    import Person._
    override def receive: Receive = {
      case Revenue(account) =>
        account ! Deposit(1000)
        account ! Withdraw(100)
        account ! Statement
        account ! Deposit(50)
        account ! Withdraw(10000)
        account ! Statement
      case message => println(message)

    }
  }
  object Person {
    case class Revenue(account: ActorRef)
  }

  def main(args: Array[String]): Unit = {
    import CounterActor._
    val system = ActorSystem("counter")
    val counter = system.actorOf(Props[CounterActor], "counter")
    (1 to 10).foreach(_ => counter ! Increment)
    (1 to 7).foreach(_ => counter ! Decrement)
    counter ! Print

    val account = system.actorOf(Props[BankAccount], "bankAccount")
//    account ! Statement
//    account ! Deposit(100)
//    account ! Statement
//    account ! Withdraw(50)
//    account ! Statement

    val person = system.actorOf(Props[Person], "person")
    person ! Revenue(account)

    // Can we assume any ordering of messages ?
    // Aren't we causing race conditions ?
    // What does "asynchronous" actually means for Actors ?
    // How does this work ?

    /**
      * - Akka has a thread pool that it shares with Actors
      * - A Few threads (100s) in the JVM can handles a big number of Actors (1000000s per GB heap). This is achieved by
      * scheduling Actors for execution on this small number of threads.
      * - Sending a message:
      *    * message is enqueued in the Actor's mailbox
      *    * thread-safe
      * - Processing a message:
      *    * A thread is scheduled to run this Actor
      *    * Messages are extracted from the mailbox, in order
      *    * The thread invokes the handler on each message
      *    * At some point, the Actor is unscheduled
      *
      * - Guarantees:
      *   * Only one thread operates on an actor at any time
      *   => Actors are effectively single-threaded.
      *   => No locks needed.
      *   * Message delivery guarantees:
      *   - At most once delivery
      *   - For any sender-receiver pair, the message order is maintained
      */
  }
}
