package gn.akka.persistence.part3.patterns_practises

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

// 1
object EventAdapters extends App {

  // Store for acoustic guitars

  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  // data structure
  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)
  // command
  case class AddGuitar(guitar: Guitar, quantity: Int)
  // event
  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
//  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    override def persistenceId: String = "guitar-inventory-manager"

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { _ =>
          addGuitarInventory(guitar, quantity)
          log.info(s"Added $quantity x $guitar to inventory")
        }
      case "print" =>
        log.info(s"Current inventory is: $inventory")
    }

    override def receiveRecover: Receive = {
//      case event @ GuitarAdded(id, model, make, quantity) =>
//        log.info(s"Recovered '$event'")
//        val guitar = Guitar(id, model, make, ACOUSTIC) // old events supports already persisted with Acoustic type
//        addGuitarInventory(guitar, quantity)
      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
        log.info(s"Recovered '$event'")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitarInventory(guitar, quantity)
    }

    def addGuitarInventory(guitar: Guitar, quantity: Int): Option[Int] = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    // ReadEventAdapter is responsible for upcasting or converting event persisted in the Journal to some other type
    /*
    Recovering :
      Journal -> Serializer -> Read Event Adapter -> Actor
      (bytes)   (GuitarAdded)   (GuitarAddedV2)   (ReceiveRecover)
     */

    override def fromJournal(event: Any, manifest: String): EventSeq = {
      // 'manifest' a type hint
      // 'EventSeq' used to slip a single event to multiple events
      event match {
        case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
          EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC))
        case other => EventSeq.single(other) // forwarding = no transformation
      }
    }
  }

  // WriteEventAdapter => Used for backward compatibility
  // Actor -> Write Event Adapter -> serializer -> Journal
  // 'toJournal' method
  // To use Both 'ReadEventAdapter' and 'WriteEventAdapter', just extend 'EventAdapter' which mixes in both traits

  val system = ActorSystem("eventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

  val guitars = for (i <- 1 to 10) yield Guitar(s"Guitar $i", s"Hakker $i", "InstrM")
//  guitars.foreach { guitar =>
//    inventoryManager ! AddGuitar(guitar, 5)
//  }
  inventoryManager ! "print"

  /*
    1- We run the app
    2- We add an additional parameter to 'Guitar' and 'GuitarAdded' called 'guitarType'
    3- We rerun the app
      Result:
        java.io.InvalidClassException: gn.akka.persistence.part3.patterns_practises.EventAdapters$GuitarAdded;
          local class incompatible...
        and it returns "'GuitarAdded': 'GuitarAdded(Guitar 1,Hakker 1,InstrM,5)'"
      Problem: Schema evolution
      Solutions:
        1- Leaving the 'GuitarAdded' event as is, without the new parameter 'guitarType', and a new 'GuitarAddedV2' that
          supports the new parameter 'guitarType', then add support for 'GuitarAddedV2' in the 'receiveRecover' method
        Drawback:
          We'll not going to get rid of the old type 'GuitarAdded'
          Everytime we make a schema change, we need to add a new event class and support it in the recover method

        2- a- Extending the trait 'ReadEventAdapter' to create a special read event adapter for the GuitarAdded old event
           'GuitarReadEventAdapter'.
           b- Remove the handling part of the 'GuitarAdded' from 'receiveRecover'
           c- Set the fully qualified name of the 'GuitarReadEventAdapter' and the old event 'GuitarAdded' inside the
           'application.conf'
         Result:
            This time the recovered result is showing 'GuitarAddedV2', not 'GuitarAdded':
            Converting from 'GuitarAdded' to 'GuitarAddedV2'
            "Recovered 'GuitarAddedV2(Guitar 1,Hakker 1,InstrM,5,acoustic)'"
   */
}
