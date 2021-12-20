package gn.akka.persistence.part1.primer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

// 3
object MultiplePersists extends App {

  /*
    Diligent accountant: with every invoice, it will persist 2 events
      - a tax record for the fiscal authority
      - an invoice record for personal logs or some auditing authority
   */

  // Command
  case class Invoice(recipient: String, date: Date, amount: Int)

  // Events
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {

    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) { record =>
          taxAuthority ! record
          latestTaxRecordId += 1
          // nested persisting
          persist("I hereby declare this tax record to be true and complete") { declaration =>
            taxAuthority ! declaration
          }
        }
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoiceRecord =>
          taxAuthority ! invoiceRecord
          latestInvoiceRecordId += 1
          // nested persisting
          persist("I hereby declare this invoice record to be true") { declaration =>
            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered: '$event'")
    }
  }
  // The best practice when creating an Actor takes parameters is to add a companion object that exposes a Props method:
  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef): Props = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received: '$message'")
    }
  }

  val system = ActorSystem("MultiplePersists")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "taxAuthority")
  val accountant = system.actorOf(DiligentAccountant.props("TAX1", taxAuthority))

  accountant ! Invoice("Furniture Corp", new Date, 1000)
  /*
  Result:
    Received: 'TaxRecord(TAX1,0,Mon Dec 20 16:04:48 CET 2021,333)'
    Received: 'InvoiceRecord(0,Furniture Corp,Mon Dec 20 16:04:48 CET 2021,1000)'

  The order is 'TaxRecord' first and then the 'InvoiceRecord'. This message ordering is GUARANTEED (and the ordering of
  the callbacks is guaranteed as well)! because Persistence is also based on message passing.
  Journals are implemented using Actors. In fact, the Journal's implementation contains a method called 'flushJournalBatch',
  that sends a message called 'WriteMessages' to an Actor called 'journal'. The method 'WriteMessages' contains a parameter
  called 'journalBatch', which contains all the events that we want to persist (in our case 'TaxRecord' and 'InvoiceRecord').
  'persist(event)' is similar to 'journal ! event'.

  Nested persisting: Order of TaxRecord, InvoiceRecord and the 2 declarations (in the 2 nested persist methods):
    Ordering of messages that reached the Journal:
      1- TaxRecord
      2- InvoiceRecord
      3- Callback of TaxRecord => Nested persist about TaxRecord declaration
      4- Callback of the InvoiceRecord => Nested persist about InvoiceRecord declaration

   Result:
     Received: 'TaxRecord(TAX1,0,Mon Dec 20 16:31:47 CET 2021,333)'
     Received: 'InvoiceRecord(0,Furniture Corp,Mon Dec 20 16:31:47 CET 2021,1000)'
     Received: 'I hereby declare this tax record to be true and complete'
     Received: 'I hereby declare this invoice record to be true'
   */

  accountant ! Invoice("Another Corp", new Date, 2000)
  /*
  Result:
      Received: 'TaxRecord(TAX1,0,Mon Dec 20 16:46:22 CET 2021,333)'
      Received: 'InvoiceRecord(0,Furniture Corp,Mon Dec 20 16:46:22 CET 2021,1000)'
      Received: 'I hereby declare this tax record to be true and complete'
      Received: 'I hereby declare this invoice record to be true'

      Received: 'TaxRecord(TAX1,1,Mon Dec 20 16:46:22 CET 2021,666)'
      Received: 'InvoiceRecord(1,Another Corp,Mon Dec 20 16:46:22 CET 2021,2000)'
      Received: 'I hereby declare this tax record to be true and complete'
      Received: 'I hereby declare this invoice record to be true'

   Technically, the second invoice will be stashed until th persisting of the first invoice is done
   */

}
