package gn.akka.persistence.part3.patterns_practises

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory
import gn.akka.persistence.part3.patterns_practises.DataModel.{WrittenCouponApplied, WrittenCouponAppliedV2}
import gn.akka.persistence.part3.patterns_practises.DomainModel.{CouponApplied, User}

import scala.collection.mutable

// 2
object DetachingModels extends App {

  /*
    Domain model: Events our Actor thinks it persists
    Data model: objects which actually get persisted
    Best practice: Make the 2 models independent
    Good side effect: Easier schema evolution to be managed
   */
  import DomainModel._

  class CouponManager extends PersistentActor with ActorLogging {

    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User]()

    override def persistenceId: String = "coupon-manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(s"Persisted '$e'")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Recovered: '$event'")
        coupons.put(code, user)
    }
  }

  val system = ActorSystem("DetachingModel", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")

//  for (i <- 10 to 15) {
//    val coupon = Coupon(s"Coupon_$i", 100)
//    val user = User(s"User_$i", s"someone_$i@gmail.com", s"name_$i")
//
//    couponManager ! ApplyCoupon(coupon, user)
//  }

  /*
    Benefits:
      - Persistence is transparent to the Actor
      - Schema evolution done in the Adapter only
   */
}

object DomainModel {
  // data structures
  case class User(id: String, email: String, name: String)
  case class Coupon(code: String, promotionAmount: Int)
  // command
  case class ApplyCoupon(coupon: Coupon, user: User)
  // Event
  case class CouponApplied(code: String, user: User)
}

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, username: String)
}

class ModelAdapter extends EventAdapter {

  // includes a type hint
  override def manifest(event: Any): String = "CMA" // any value you want: CouponManagerAdapter - CMA

  // Actor -> toJournal -> Serializer -> Journal
  override def toJournal(event: Any): Any =
    event match {
      case event @ CouponApplied(code, user) =>
        println(s"Converting '$event' to data model")
//        WrittenCouponApplied(code, user.id, user.email)
        WrittenCouponAppliedV2(code, user.id, user.email, user.name)
    }

  // Journal -> Serializer -> fromJournal -> Actor
  override def fromJournal(event: Any, manifest: String): EventSeq =
    event match {
      case event @ WrittenCouponApplied(code, userId, userEmail) =>
        println(s"Converting '$event' to domain model")
//        EventSeq.single(CouponApplied(code, User(userId, userEmail)))
        EventSeq.single(CouponApplied(code, User(userId, userEmail, "")))
      case event @ WrittenCouponAppliedV2(code, userId, userEmail, username) =>
        println(s"Converting '$event' to domain model")
        EventSeq.single(CouponApplied(code, User(userId, userEmail, username)))
      case other => EventSeq.single(other)
    }
}
