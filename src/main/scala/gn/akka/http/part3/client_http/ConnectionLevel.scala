package gn.akka.http.part3.client_http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import gn.akka.http.part3.client_http.PaymentSystemDomain.PaymentRequest

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 1'
object ConnectionLevel extends App with PaymentJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("ConnectionLevel")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest()).onComplete {
    case Success(response)  => println(s"Got successful response: $response")
    case Failure(exception) => println(s"Sending the request failure: $exception")
  }
  // Result: Got successful response: HttpResponse(200 OK,List(Date: Wed, 15 ....

  // A small payment system:
  val creditCards = List(
    CreditCard("1254-1254-1254-1254", "1254", "pm-dm-ml-pm"),
    CreditCard("1234-1234-1234-1234", "1254", "pm-ml-md-pm"),
    CreditCard("5689-5689-5689-5689", "1254", "ml-dm-md-pm")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "store-account", 500))
  val serverHttpRequests = paymentRequests.map(paymentRequest => {
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity = HttpEntity(ContentTypes.`application/json`, paymentRequest.toJson.prettyPrint)
    )
  })

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8010))
    .to(Sink.foreach[HttpResponse](println))
    .run()

  /*
    Run :
      1- PaymentSystem
      2- ConnectionLevel
      3- See both consoles
   */
}
