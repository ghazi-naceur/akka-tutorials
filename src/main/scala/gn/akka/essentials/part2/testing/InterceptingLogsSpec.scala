package gn.akka.essentials.part2.testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

// 4
class InterceptingLogsSpec
    extends TestKit(ActorSystem("InterceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages")))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import InterceptingLogsSpec._
  val item = "Akka course"
  val creditCard = "1234-5678-1234-5678"
  val invalidCreditCard = "0000-5678-1234-5678"
  "A checkout flow" should {
    "correctly log the dispatch of an order" in {
      EventFilter.info(pattern = s"Order '[0-9]+' for item '$item' has been dispatched.", occurrences = 1) intercept {
        // our test code
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, creditCard)
      }
      // This creates and EventFilter object, which scan for logs messages at 'info' level.
      // Every log message that matches this pattern will be caught by the EventFilter
      // We need to configure the right logger, in order to help the EventFilter to catch it. We configure it using a
      // config property in the application.conf file, and loading it inside the 'TestKit' trait
    }

    "throw error if payment denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, invalidCreditCard)
      }
    }
  }
}

object InterceptingLogsSpec {

  case class Checkout(item: String, creditCard: String)
  case class AuthorizeCard(creditCard: String)
  case object PaymentAccepted
  case object PaymentDenied
  case class DispatchOrder(item: String)
  case object OrderConfirmed

  class CheckoutActor extends Actor {
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, creditCard) =>
        paymentManager ! AuthorizeCard(creditCard)
        context.become(pendingPayment(item))
    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied => throw new RuntimeException("Error occurred with payment")
    }

    def pendingFulfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }
  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case AuthorizeCard(card) =>
        if (card.startsWith("0")) sender() ! PaymentDenied
        else sender() ! PaymentAccepted
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderId = 0

    override def receive: Receive = {
      case DispatchOrder(item) =>
        orderId += 1
        log.info(s"Order '$orderId' for item '$item' has been dispatched.")
        sender() ! OrderConfirmed
    }
  }
}

/**
  * 'CheckoutActor' starts with the 'awaitingCheckout' message handler, which excepts 'Checkout(item, creditCard)'.
  * Whenever I receive a 'Checkout(item, creditCard)' message, I will send an 'AuthorizeCard(creditCard)' message to my
  * 'paymentManager' child, and then I will switch my Receive message handler to 'pendingPayment(item)'.
  * In the meantime, my 'PaymentManager' will reply with a 'PaymentAccepted' or 'PaymentDenied', which is handled in the
  * 'receive' message handler method. If I receive 'PaymentAccepted', then I'm free to move on with a Dispatch of my
  * order, which means I'm sending a 'DispatchOrder(item)' message to my 'fulfillmentManager' child, and I'm switch my
  * receive message handler to 'pendingFulfillment'.
  * In the meantime, my 'FulfillmentManager' receives my 'DispatchOrder(item)' and sends me back an 'OrderConfirmed', which
  * I'm handling in the 'pendingFulfillment' message handler method, and if I do so I'm switching back to my receive message
  * handler 'awaitingCheckout'.
  *
  * Let's say in the end of this flow, in the 'FulfillmentManager', we want to log a message.
  * 'import InterceptingLogsSpec._'  can help us to intercept and test the whole flow, which is difficult to test each
  * part separately.
  */
