package gn.akka.http.part3.client_http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import gn.akka.http.part3.client_http.PaymentSystemDomain.PaymentRequest

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * This example won't work because I upgraded the Akka version from 2.5.x to 2.6.x
  */
// 2
object HostLevelAPI extends App with PaymentJsonProtocol {

  /*
      Multiple client API levels:
          - connection-level API
          - host-level API
          - request-level API

      Host-level API benefits:
        - the freedom from managing individual connections
        - the ability to attach data to requests(aside from payloads)

      Host-level API should be used for high-volume and low-latency requests
   */

  implicit val system: ActorSystem = ActorSystem("HostLevelAPI")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")
  Source(1 to 10)
    .map(i => (HttpRequest(), i)) // tuple of (HttpRequest, num)
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        response.discardEntityBytes()
        //The statement above is very important, because if I don't do that, I'll block the connection that the 'response'
        // wants to get through which will lead to leaking connection the connection pool 'cachedHostConnectionPool'
        s"Request '$value' has received response: '$response'"
      case (Failure(exception), value) =>
        s"Request '$value' has failed due to: '$exception'"
    }
    .runWith(Sink.foreach[String](println))

  val creditCards = List(
    CreditCard("1254-1254-1254-1254", "1254", "pm-dm-ml-pm"),
    CreditCard("1234-1234-1234-1234", "1254", "pm-ml-md-pm"),
    CreditCard("5689-5689-5689-5689", "1254", "ml-dm-md-pm")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "store-account", 500))
  val serverHttpRequests = paymentRequests.map(paymentRequest => {
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(ContentTypes.`application/json`, paymentRequest.toJson.prettyPrint)
      ),
      UUID.randomUUID().toString
    )
  })

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8020))
    .runForeach {
      case (Success(response @ HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The order id '$orderId'  was not allowed to proceed and returned the response: '$response'")
      case (Success(response), orderId) =>
        println(s"The order id '$orderId'  was successful and returned the response: '$response'")
      // you can do something with the order ID ...
      case (Failure(exception), orderId) =>
        println(s"The order id '$orderId'  could not be complete due to : '$exception'")
    }

  /*
  Run :
    1- PaymentSystem
    2- HostLevelAPI
    3- See both consoles
   */
}
