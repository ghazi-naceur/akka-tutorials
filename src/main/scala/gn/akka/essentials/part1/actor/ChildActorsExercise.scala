package gn.akka.essentials.part1.actor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

// 7
object ChildActorsExercise {

  // Distributed Word counting
  class WordCounterMaster extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case Initialize(nbChildren) =>
        println("[master] Initializing...")
        val childrenRefs = for (i <- 1 to nbChildren) yield context.actorOf(Props[WordCounterWorker], s"wcw_$i")
        context.become(withChildren(childrenRefs, 0, 0, Map()))
    }

    def withChildren(
      childrenRefs: Seq[ActorRef],
      currentChildIndex: Int,
      currentTaskId: Int,
      requestMap: Map[Int, ActorRef]
    ): Receive = {
      case text: String =>
        println(s"[master] I have received '$text', and I will send it to child '$currentChildIndex'")
        val originalSender = sender()
        val task = WordCountTask(currentTaskId, text)
        val childRef = childrenRefs(currentChildIndex)
        childRef ! task
        val nextChildIndex = (currentChildIndex + 1) % childrenRefs.length // rotate for round robin
        val newTaskId = currentTaskId + 1
        // We need to track which task id maps to which original sender
        val newRequestMap = requestMap + (currentTaskId -> originalSender)
        context.become(withChildren(childrenRefs, nextChildIndex, newTaskId, newRequestMap))
      case WordCountReply(id, count) =>
        // Problem: Who is the original request to send the 'count' to ? It isn't 'sender', because it's the Child who
        // sent the WCReply .. I lost track of the original sender, so we need to keep track of the original requester
        // of work
        // Normally, when we send tasks and we expect for replies from our many worker actors, we actually assign each
        // task with an Identification Number, which is why we need to pass to 'WordCountTask()' and 'WordCountReply()'
        // messages, so that the master actor can keep track of which task got which reply
        println(s"[master] I have received a reply for task id '$id' with count '$count'")
        val originalSender = requestMap(id)
        originalSender ! count
        context.become(withChildren(childrenRefs, currentChildIndex, currentTaskId, requestMap - id))
    }
  }
  object WordCounterMaster {
    case class Initialize(nbChildren: Int)
    case class WordCountTask(id: Int, text: String)
    case class WordCountReply(id: Int, count: Int)
  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case WordCountTask(id, text) =>
        println(s"[child] ${self.path} -  I have received a task '$id' with text '$text' ")
        sender() ! WordCountReply(id, text.split(" ").length)
    }
  }

  class TestActor extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(5)
        val texts = List(
          "This is a test text",
          "Learning Akka is interesting",
          "This is a tutorial",
          "This",
          "Exploring Akka",
          "This a new thing"
        )
        texts.foreach(text => master ! text)
      case count: Int =>
        println(s"[test-actor] I've received a reply: '$count'")
    }
  }

  def main(args: Array[String]): Unit = {
    /*
    create WCMaster
    send Initialize(10) to WCWorker
    send "akka is interesting" to WCMaster
      WCMaster will send WCTask("akka is interesting") to one of its children
        WCWorker replies with a WCReply(3) to WCMaster
      WCMaster replies with 3 to the sender

    requester --> WCMaster --> WCWorker
    requester <-- WCMaster <-- WCWorker

    To achieve the Load Balancing within the workers, we can the Round Robin Logic
     */
    val system = ActorSystem("roundRobinWordCount")
    val testActor = system.actorOf(Props[TestActor], "testActor")
    testActor ! "go"
  }
}
