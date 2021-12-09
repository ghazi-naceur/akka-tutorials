package gn.akka.streams.part3.techniques_and_patterns

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 3
object AdvancedBackpressure extends App {
  implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")
  import akka.stream.Materializer.matFromSystem

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nbInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements", new Date),
    PagerEvent("Number of http 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date)
  )
  val eventSource = Source(events)

  val onCallEngineer = "isaac@hxh.com"

  def sendEmail(notification: Notification): Unit =
    println(s"Hi ${notification.email}, you have an event ${notification.pagerEvent}")

  val notificationSink =
    Flow[PagerEvent].map(event => Notification(onCallEngineer, event)).to(Sink.foreach[Notification](sendEmail))
  // standard
//  eventSource.to(notificationSink).run()

  // Imaging that 'notificationSink' is slow for some reason, in that case it will try to backpressure the source, but
  // this may cause some issues, because in the practical side, the relevant engineer should be paged by the service
  // and may be not paged on time, and for Akka stream consideration, this 'eventSource' may actually not be able to
  // backpressure, especially if it is 'Timer Based' ==> Timer Based sources do not respond to backpressure

  /*
    Solution for fast producer and slow consumer: conflate
   */
  // Unbackppressurable source:
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Hi ${notification.email}, you have an event ${notification.pagerEvent}")
  }
  /*
  If a Source can't be backpressured, as a solution you need to somehow aggregate events and create 1 single notification
  when we receive demand from the sink. So instead of buffering the event on our flow (when we have a 'slow sink +
  unbackpressurable source') here, 'Flow[PagerEvent]...' and create 3 notifications for 3 different 3 events, we can create
  a single event notification for multiple events, so we can aggregate the Pager Events and create a single notification
  out of them, and for that we should use a method called 'conflate', which acts like 'fold' in a way. It can bind elements,
  but it only emits the result, when the downstream (sink) sends on their demand
   */
  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nbInstances = event1.nbInstances + event2.nbInstances
      PagerEvent(s"You have $nbInstances events that require your attention", new Date, nbInstances)
    })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()
  // The effect of conflate here in this example, won't be visible, unless we integrate an '.async', so that can the
  // 'eventSource' and the 'aggregateNotificationFlow' run on a separate actor, and the rest on a separate actor as well.
  // With 'conflate', we can avoid the backpressure and basically in this example, we're decoupling the upstream rate
  // (source) from the downstream rate (sink) => An alternative to backpressure

  /*
  Solution for slow producer and fast consumer: extrapolate/expand
   */
  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val fastSink = Sink.foreach[Int](println)
  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  // The flow will take an element and produce artificially more elements, until it receives a new element from the slow source
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))
  // The flow will take an element and repeat it continuously, until it receives a new element from the slow source
//  slowCounter.via(extrapolator).to(fastSink).run()
  slowCounter.via(repeater).to(fastSink).run()

  val expander = Flow[Int].expand(element => Iterator.continually(element))
  // The 'expand' method is similar to 'extrapolate', only with 1 difference: 'expand' creates the Iterator at all times
  // in the other hand, 'extrapolate' creates the Iterator only when a demand is met
}
