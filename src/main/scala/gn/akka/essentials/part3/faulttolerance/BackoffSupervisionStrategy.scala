package gn.akka.essentials.part3.faulttolerance

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.language.postfixOps

// 5
object BackoffSupervisionStrategy {
  /*
  BackoffSupervisionStrategy tries to solve the repeated restarts of Actors, especially in the case when actors trying
  to interact with external resources, like Database. If a database is down, restarting actors immediately might do more
  harm than good.
   */

  case object ReadFile

  class FileBasedPersistentActor extends Actor with ActorLogging {

    var dataSource: Source = null

    override def preStart(): Unit = log.info("Persistent actor starting")
    override def postStop(): Unit = log.warning("Persistent actor has stopped")
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null) {
          dataSource = Source.fromFile(new File("src/main/resources/data/not_found_file.txt"))
          log.info(s"I've just read some important data: ${dataSource.getLines().toList}")
        }
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("BackoffSupervisorDemo")
    val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")

    simpleActor ! ReadFile
    /*
    As result, an exception will be thrown because the file does not exist. Akka Actor is self-healing, so it will
    restart the actor again. In a real world case, there would a restart for a big load of Actors, and this can be problematic.
    The solution is to use the Backoff supervision strategy.
     */

    // The supervision strategy will kick in on the failure of the actor
    val simpleSupervisorProps = BackoffSupervisor.props(
      Backoff.onFailure(Props[FileBasedPersistentActor], "simpleBackoffActor", 3 seconds, 30 seconds, 0.2)
    )

    val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
    /*
    The 'simpleSupervisor' will create a child, called 'simpleBackoffActor', which is based on the props 'simpleSupervisorProps'.
    The 'simpleSupervisor' can receive any message and forward them to its child 'simpleBackoffActor'.
    The supervision strategy is the default one, which is basically (restarting on everything).
    When the child actor fails, the supervision strategy kicks in (the first attempt) after 3 seconds (minBackoff),
    if the child actor fails again, the next attempt is 2x the previous attempt (after 6s), etc (12s, 24s ), until it
    reaches the cap which is 30 seconds (maxBackoff). 0.2 is a randomness factor, which adds a little bit of noise to the
    minBackoff (3s), so that we don't have a huge amount of actors starting off at that exact moment.
     */
    simpleBackoffSupervisor ! ReadFile // the ReadFile message will be forwarded to the child 'simpleBackoffActor'.
    // It will fail and after 3s, it will restart again
    /**
[WARN] [12/01/2021 20:35:04.538] [BackoffSupervisorDemo-akka.actor.default-dispatcher-5] [akka://BackoffSupervisorDemo/user/simpleActor] Persistent actor restarting
[WARN] [12/01/2021 20:35:04.539] [BackoffSupervisorDemo-akka.actor.default-dispatcher-4] [akka://BackoffSupervisorDemo/user/simpleSupervisor/simpleBackoffActor] Persistent actor has stopped
[INFO] [12/01/2021 20:35:04.539] [BackoffSupervisorDemo-akka.actor.default-dispatcher-5] [akka://BackoffSupervisorDemo/user/simpleActor] Persistent actor starting
[INFO] [12/01/2021 20:35:07.601] [BackoffSupervisorDemo-akka.actor.default-dispatcher-3] [akka://BackoffSupervisorDemo/user/simpleSupervisor/simpleBackoffActor] Persistent actor starting
      */

    // The supervision strategy will kick in on the stop of the actor
    val stopSupervisorProps = BackoffSupervisor.props(
      Backoff
        .onStop(Props[FileBasedPersistentActor], "stopBackoffActor", 3 seconds, 30 seconds, 0.2)
        .withSupervisorStrategy(OneForOneStrategy() {
          case _ => Stop
        })
    )

    val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
    stopSupervisor ! ReadFile

    /**
[ERROR] [12/01/2021 20:55:06.154] [BackoffSupervisorDemo-akka.actor.default-dispatcher-3] [akka://BackoffSupervisorDemo/user/stopSupervisor/stopBackoffActor] src/main/resources/data/not_found_file.txt (No such file or directory)
[WARN] [12/01/2021 20:55:06.156] [BackoffSupervisorDemo-akka.actor.default-dispatcher-5] [akka://BackoffSupervisorDemo/user/stopSupervisor/stopBackoffActor] Persistent actor has stopped
[INFO] [12/01/2021 20:55:09.765] [BackoffSupervisorDemo-akka.actor.default-dispatcher-6] [akka://BackoffSupervisorDemo/user/stopSupervisor/stopBackoffActor] Persistent actor starting
      */

    class EagerFileBasedPersistentActor extends FileBasedPersistentActor {
      override def preStart(): Unit = {
        log.info("Eager actor starting")
        dataSource = Source.fromFile(new File("src/main/resources/data/not_found_file.txt"))
      }
    }

    val eagerActor = system.actorOf(Props[EagerFileBasedPersistentActor], "eagerActor")
    // Akka will throw an 'ActorInitializationException' (preStart), based on 'FileNotFoundException'.
    // This can be handled by the supervision strategy.
    // The default strategy with 'ActorInitializationException' is the Akka Directive 'Stop', because the 'eagerActor'
    // is not started again:
    /*
    We can create a Backoff supervisor actor that, 'onStop' it will start this 'eagerActor' again, in an exponential delay.
    For that, instead of creating the 'eagerActor' directly, we can do:
     */

    val repeatedSupervisorProps = BackoffSupervisor.props(
      Backoff.onStop(Props[EagerFileBasedPersistentActor], "repeatedEagerActor", 1 second, 30 seconds, 0.1)
    )

    val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "repeatedSupervisor")
//    The line above creates 'repeatedSupervisor' and a child 'repeatedEagerActor', which will die on start with
//    ActorInitializationException. It will trigger the supervision strategy in 'eagerSupervisor', which is to stop
//    'repeatedEagerActor', and then the Backoff will kick in after 1 second, after the 'repeatedEagerActor' being dead.
//    etc .. So the 'repeatedEagerActor' will continuously die in 1s, 2s, 4s, 8s, 16s. (the cap is 30, so the last attempt
//    will be after 16s)

  }
}
