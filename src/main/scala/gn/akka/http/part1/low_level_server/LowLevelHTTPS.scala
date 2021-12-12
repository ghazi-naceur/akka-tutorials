package gn.akka.http.part1.low_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import gn.akka.http.part1.low_level_server.HttpsContext.httpsConnectionContext

import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContext {
  // step1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("https/keystore.pkcs12")
  // as an alternative:  new FileInputStream(new File("src/main/resources/https/keystore.pkcs12"))
  val password: Array[Char] = "pass".toCharArray // the password that unlocks this keystore
  ks.load(keyStoreFile, password)

  // step2: initialize a key manager
  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI: Public Key Infrastructure
  keyManagerFactory.init(ks, password)

  // step3: initialize a trust manager
  val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // step4: initialize an SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom())

  // step5: return the https connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)
}

// 4
object LowLevelHTTPS extends App {
  implicit val system: ActorSystem = ActorSystem("LowLevelHTTPS")
  import akka.stream.Materializer.matFromSystem

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                               |<html>
                                                               |  <body>
                                                               |     Hello from Akka HTTP!
                                                               |  </body>
                                                               |</html>
                                                               |""".stripMargin)
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                               |<html>
                                                               |  <body>
                                                               |     Resource can't be found.
                                                               |  </body>
                                                               |</html>
                                                               |""".stripMargin)
      )
  }

  val httpsBinding = Http().bindAndHandleSync(requestHandler, "localhost", 8443, httpsConnectionContext)
  // Run and go to https://localhost:8443/
}
