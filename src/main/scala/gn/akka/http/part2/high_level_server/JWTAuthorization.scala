package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.Timeout
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)
  implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
}

// 11
/**
object JWTAuthorization extends App with SprayJsonSupport {
  implicit val system: ActorSystem = ActorSystem("JWTAuthorization")
  import akka.stream.Materializer.matFromSystem
  import akka.http.scaladsl.server.Directives._
  import system.dispatcher
  implicit val timeout: Timeout = Timeout(2 seconds)
  import SecurityDomain._

  val superSecretPasswordDb = Map("admin" -> "admin", "isaac" -> "Somethepas1.")

  val algorithm = JwtAlgorithm.ES256
  val secretKey = "issacneter"

  def checkPassword(username: String, password: String): Boolean =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("isaac.netero")
//      content = "" // adding json payload to represent the permissions that this token is associated with
    )
    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
  }

  def isTokenExpired(token: String): Boolean =
    JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
      case Failure(_)      => true
    }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) if isTokenExpired(token) =>
          complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired"))
        case Some(token) if isTokenValid(token) =>
          complete("User accessed authorized endpoint")
        case _ =>
          complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token invalid, or has been tampered with"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8011)

  /*
      curl -XPOST http://localhost:8011 -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/jwt/login.json"
      curl -XPOST localhost:8011 -H "Content-Type: application/json" --data-binary "@/home/ghazi/workspace/akka-tutorials/src/main/resources/jwt/login.json"

  */
}
  */
object JwtAuthorization extends App with SprayJsonSupport {

  implicit val system: ActorSystem = ActorSystem("JWTAuthorization")
  import akka.stream.Materializer.matFromSystem
  import akka.http.scaladsl.server.Directives._
  import system.dispatcher
  implicit val timeout: Timeout = Timeout(2 seconds)
  import SecurityDomain._

  val superSecretPasswordDb = Map("admin" -> "admin", "daniel" -> "Rockthejvm1!")

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "rockthejvmsecret"

  def checkPassword(username: String, password: String): Boolean =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("rockthejvm.com")
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
  }

  def isTokenExpired(token: String): Boolean =
    JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
      case Failure(_)      => true
    }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token)) {
            if (isTokenExpired(token)) {
              complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired."))
            } else {
              complete("User accessed authorized endpoint!")
            }
          } else {
            complete(
              HttpResponse(status = StatusCodes.Unauthorized, entity = "Token is invalid, or has been tampered with.")
            )
          }
        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No token provided!"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)

  /*

  1- Execute:
    http POST localhost:8080 < /home/ghazi/workspace/akka-tutorials/src/main/resources/jwt/login.json

  2- Copy token:
    eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJyb2NrdGhlanZtLmNvbSIsImV4cCI6MTYzOTY4NTI5NSwiaWF0IjoxNjM5NTk4ODk1fQ.w0uLehMbi1KE4VWrVxS_CplZdjQ_yUR9M_ZMZu6IuyQ

  3- Execute:
    http GET localhost:8080/secureEndpoint "Authorization: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJyb2NrdGhlanZtLmNvbSIsImV4cCI6MTYzOTY4NTI5NSwiaWF0IjoxNjM5NTk4ODk1fQ.w0uLehMbi1KE4VWrVxS_CplZdjQ_yUR9M_ZMZu6IuyQ"
   */
}
