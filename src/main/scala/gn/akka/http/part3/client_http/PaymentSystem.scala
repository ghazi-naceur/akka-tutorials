package gn.akka.http.part3.client_http

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.util.Timeout
import gn.akka.http.part3.client_http.PaymentSystemDomain.{PaymentAccepted, PaymentRejected, PaymentRequest}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

case class CreditCard(serialNumber: String, securityCode: String, account: String)

object PaymentSystemDomain {
  case class PaymentRequest(creditCard: CreditCard, receiverAccount: String, amount: Double)
  case object PaymentAccepted
  case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {
  implicit val creditCardFormat: RootJsonFormat[CreditCard] = jsonFormat3(CreditCard)
  implicit val paymentRequestFormat: RootJsonFormat[PaymentRequest] = jsonFormat3(PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {
  import PaymentSystemDomain._
  override def receive: Receive = {
    case PaymentRequest(CreditCard(serialNumber, _, senderAccount), receiverAccount, amount) =>
      log.info(s"The '$senderAccount' is trying to send '$amount' to '$receiverAccount'")
      if (serialNumber == "1234-1234-1234-1234")
        sender() ! PaymentRejected
      else sender() ! PaymentAccepted
  }
}

// 1"
object PaymentSystem extends App with PaymentJsonProtocol with SprayJsonSupport {

  // Microservice for payments
  implicit val system: ActorSystem = ActorSystem("PaymentSystem")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)

  val paymentValidator = system.actorOf(Props[PaymentValidator], "paymentValidator")

  val paymentRoute =
    path("api" / "payments") {
      post {
        entity(as[PaymentRequest]) { paymentRequest =>
          val validationResponseFuture = (paymentValidator ? paymentRequest).map {
            case PaymentRejected => StatusCodes.Forbidden
            case PaymentAccepted => StatusCodes.OK
            case _               => StatusCodes.BadRequest
          }
          complete(validationResponseFuture)
        }
      }
    }

  Http().bindAndHandle(paymentRoute, "localhost", 8010)

  /*
    curl -XPOST http://localhost:8010/api/payments -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/client/paymentRequest.json"
      ==> OK
    curl -XPOST http://localhost:8010/api/payments -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/client/invalidPaymentRequest.json"
      ==> Forbidden
   */
}
