package gn.akka.streams.part3.techniques_and_patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 2
object IntegratingWithExternalServices extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  import akka.stream.Materializer.matFromSystem
//  import system.dispatcher // not recommended in practice for 'mapAsync', instead, you should use you're own dispatcher:
  implicit val dispatcher: MessageDispatcher = system.dispatchers.lookup("dedicated-dispatcher")
  // 'dedicated-dispatcher' is defined in the 'application.conf' config file

  def genericExtService[A, B](data: A): Future[B] = ???

  // Example: Simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(
    List(
      PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
      PagerEvent("FastDataPipeline", "Illegal elements", new Date),
      PagerEvent("AkkaInfra", "Not responding", new Date),
      PagerEvent("SuperFrontend", "Button doesn't work", new Date)
    )
  )

  object PagerService {
    private val engineers = List("Isaac", "Shisui", "Itachi")
    private val emails =
      Map("Isaac" -> "isaac@hxh.com", "Shisui" -> "shisui@konoha.com", "Itachi" -> "itachi@konoha.com")

    def processEvent(pagerEvent: PagerEvent): Future[String] =
      Future {
        val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
        val engineer = engineers(engineerIndex.toInt)
        val engineerEmail = emails(engineer)

        println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
        Thread.sleep(1000)
        engineerEmail
      }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  // We can't use a map or a regular flow on these events (to send them to the engineer), because our external service
  // 'processEvent' can only return a Future, so we'll use:
  val pagerEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
  // 'parallelism' determines how many Future can run at the same time, and if one of these futures fails, the whole stream
  // will fail
  // We're running multiple futures at the same time, so there is a question about the ordering of these events:
  // Fortunately, 'mapAsync' guarantees the relative order of the elements, regardless of which future is faster or slower
  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
//  pagerEngineerEmails.to(pagedEmailsSink).run()
  // Increasing parallelism can improve your throughput
  // All futures are evaluated in parallel, but 'mapAsync' always waits for the future that suppose to give its value

  // Running futures in streams implies that we may end up running a lot of these futures, so it's important that if you
  // run futures in Akka stream, you should run them in their own execution context, not in the actor system dispatcher
  // because you may starve it for threads

  // Transforming the 'PagerService' object to Actor:
  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Isaac", "Shisui", "Itachi")
    private val emails =
      Map("Isaac" -> "isaac@hxh.com", "Shisui" -> "shisui@konoha.com", "Itachi" -> "itachi@konoha.com")

    // 'processEvent' was returning 'Future[String]' when it was defined inside in the PagerService object. Now in the
    // actor, we don't need to return 'Future[String]', because we don't need another level of asynchronous computing.
    // Actors are already asynchronous.
    private def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  implicit val timeout: Timeout = Timeout(3 seconds)
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  // '.mapTo[String]' is added to fix the expected type, which will help us the pass the right type inside the statement
  // below:
  alternativePagedEngineerEmails.to(pagedEmailsSink).run()

  // Do not confuse 'mapAsync' with 'async'(Async boundary, that makes a component or a chain from Akka stream run on a
  // separate Actor)
}
