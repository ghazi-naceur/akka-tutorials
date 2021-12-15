package gn.akka.http.part3.client_http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import gn.akka.http.part3.client_http.PaymentSystemDomain.PaymentRequest

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * This example won't work because I upgraded the Akka version from 2.5.x to 2.6.x
  */
// 3
object RequestLevelAPI extends App with PaymentJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("RequestLevelAPI")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  /*
      Request-level API benefits:
        - the freedom from managing anything
        - super simple to use
   */

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))
  responseFuture.onComplete {
    case Success(response) =>
      response.discardEntityBytes() // important
      println(s"The request was successful and returned: $response")
    case Failure(exception) => println(s"The request failed due to: $exception")
  }

  val creditCards = List(
    CreditCard("1254-1254-1254-1254", "1254", "pm-dm-ml-pm"),
    CreditCard("1234-1234-1234-1234", "1254", "pm-ml-md-pm"),
    CreditCard("5689-5689-5689-5689", "1254", "ml-dm-md-pm")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "store-account", 500))
  val serverHttpRequests = paymentRequests.map(paymentRequest => {
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8010/api/payments",
      entity = HttpEntity(ContentTypes.`application/json`, paymentRequest.toJson.prettyPrint)
    )
  })

  Source(serverHttpRequests)
    .mapAsync(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
