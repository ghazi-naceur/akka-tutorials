package gn.akka.essentials.part5.patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout
import gn.akka.essentials.part5.patterns.AskSpec.AuthManager.{
  AUTH_FAILURE_NOT_FOUND,
  AUTH_FAILURE_PASSWORD_INCORRECT,
  AUTH_FAILURE_SYSTEM
}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.{existentials, postfixOps}
import scala.util.{Failure, Success}
// step1- import ask method
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import AskSpec._

  "An authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "A piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props): Unit = {
    "fail to authenticate a unregistered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("Isaac", "Netero")

      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("Isaac", "Netero")
      authManager ! Authenticate("Isaac", "badpassword")

      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "successfully authenticate a registered user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("Isaac", "Netero")
      authManager ! Authenticate("Isaac", "Netero")

      expectMsg(AuthSuccess)
    }
  }
}

object AskSpec {

  case class Read(key: String)
  case class Write(key: String, value: String)

  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at the key '$key'")
        sender() ! kv.get(key) // Option[String]
      case Write(key, value) =>
        log.info(s"Writing the value '$value' for the key '$key'")
        context.become(online(kv + (key -> value)))
    }
  }

  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess

  class AuthManager extends Actor with ActorLogging {

    // step2- logistics
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    protected val authDb: ActorRef = context.actorOf(Props[KVActor])

    override def receive: Receive = {
      case RegisterUser(username, password) => authDb ! Write(username, password)
      case Authenticate(username, password) =>
        /**  authDb ! Read(username)
        context.become(waitingForPassword(password, sender())) */
        handleAuthentication(username, password)

    }
    def handleAuthentication(username: String, password: String): Unit = {
      val originalSender = sender() // sender of the Authenticate
      // step3- ask the actor with '?'
      val future = authDb ? Read(username)
      /*
      This line above is a future, so it runs in a separate thread.
      The lines below that handles the 'onComplete' method run as well in a separate thread. It might the same thread
      that's using to handle the message, or might be a different thread, but regardless who is the sender of the message
      when the future has completed ?? it probably should be the 'authDb', not the one that tries to authenticate
      and this case is simple enough, because the 'sender()' returns the 'ActorRef' who lastly send a message, but what
      if I had accessed a mutable state or called some other method inside this actor in the future onComplete callback,
      then that will cause a Race Condition between threads ==> This breaks the Actor Encapsulation, and for this reason
      we need to add this note:
        NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE
      In our case, we can save the result of the sender, before the future complete in a 'originalSender' val , and using it
      instead of using 'sender()' directly. If we don't do that, we risk what's called Closing Over the actor instance
      the actor instance or Mutable State
       */
      // step4- handle the future
      future.onComplete {
        case Success(None) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          if (dbPassword == password)
            originalSender ! AuthSuccess
          else {
            originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
          }
        case Failure(_) => originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }

    /**
    def waitingForPassword(password: String, ref: ActorRef): Receive = {
      case password: Option[String] => // do password checks here
      // This is really painful, because we're working in a distributed environment, so this AuthManager might be
      // bombarded with Authenticate requests, so I always need to keep track of which user try to authenticate
      // with what password and what time (maybe the request timeout, or maybe something broke down, maybe some user
      // try to register out the system and in the meantime).
      // Another issue, the KVActor only sends back an Option[String], so in order to keep track of which user is trying
      // to authenticate with what password, I'd either need to keep state in the Receive message handler or try to
      // somehow hack this KVActor to extend it for this use case
      // We wont use this method. Instead we'll use the Ask method (?)
    }
      */
  }

  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(username: String, password: String): Unit = {
      val future = authDb ? Read(username)
      // the future is typed Future[Any], because the compiler doesn't know in advance what kind of message may complete
      // this future .. We can improve it by strengthening the type
      val passwordFuture = future.mapTo[Option[String]] // Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbPassword) =>
          if (dbPassword == password)
            AuthSuccess
          else
            AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      }
      // pipeTo: When the future completes, send back the response of the future to the actor ref
      responseFuture.pipeTo(sender())
    }
  }

  object AuthManager {
    val AUTH_FAILURE_NOT_FOUND = "Password not found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "Password incorrect"
    val AUTH_FAILURE_SYSTEM = "System error"
  }
}
