package gn.akka.persistence.part2.stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

// Command
case class RegisterUser(email: String, name: String)
// Event
case class UserRegistered(id: Int, email: String, name: String)

// serializer
class UserRegistrationSerializer extends Serializer {

  val SEPARATOR = "//"

  override def identifier: Int = 123456789 // whatever number you want to identify this serializer

  // UserRegistered -> Array[Byte]
  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case event @ UserRegistered(id, email, name) =>
        println(s"Serializing '$event'")
        s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes()
      case _ => throw new IllegalArgumentException("Only the UserRegistered events are supported in this serializer")
    }
  }

  override def includeManifest: Boolean = false
  // if 'includeManifest' == true, the 'manifest' parameter in 'fromBinary' will contain a class, if == false, no class
  // will be passed, so 'None'

  // Array[Byte] -> UserRegistered
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(SEPARATOR)
    val id = values.head.toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"Deserialized '$string' to '$result'")
    result
  }
  // Uses Reflection API in 'manifest'
}

class UserRegistrationActor extends PersistentActor with ActorLogging {

  var currentId = 0

  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(s"Persisted: '$e'")
      }
  }

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"Recovered: '$event'")
      currentId = id
  }
}

// 4
object CustomSerialization extends App {
  /*
   Java serialization is used by default
   Serialization = turning in-memory objects into a recognizable format
   Java serialization is not ideal:
      - memory consumption
      - speed/performance

   Many Akka serializers are out of date

   Workflow of a serializer when persisting:
      1- Send Command to the Actor
      2- Actor calls 'persist'
      3- Serializer serializes the event into bytes using 'toBinary' method from the Serializer
      4- The journal writes the bytes

   Workflow of a serializer when recovering:
      1- Reading bytes from Journal
      2- Serializer deserializes the bytes using 'fromBinary' method from the Serializer
   */
  val system = ActorSystem("CustomSerialization", ConfigFactory.load().getConfig("customSerializerDemo"))
  val userRegistrationActor = system.actorOf(Props[UserRegistrationActor], "userRegistrationActor")

//  for (i <- 1 to 10) {
//    userRegistrationActor ! RegisterUser(s"isaac.netero.$i@hxh.com", s"Isaac Netero - $i")
//  }

  /*
    1- Run this command in the CQL:
      select * from akka.messages;

    2- Pick some binary data, for example: 0x5b302f2f69736161632e6e657465726f2e31406878682e636f6d2f2f4973616163204e657465726f202d20315d
    3- Enter this website 'https://codebeautify.org/hex-string-converter' and copy the last message in step 2, to convert
      hex data to string:
      => Result:
        0x5b302f2f69736161632e6e657465726f2e31406878682e636f6d2f2f4973616163204e657465726f202d20315d
        is
        [0//isaac.netero.1@hxh.com//Isaac Netero - 1]


     When recovering:
        Result:
           Deserialized '[0//isaac.netero.1@hxh.com//Isaac Netero - 1]' to 'UserRegistered(0,isaac.netero.1@hxh.com,Isaac Netero - 1)'
   */

}
