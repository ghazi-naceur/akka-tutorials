package gn.akka.persistence.part2.stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

// 1
object LocalStores extends App {

  val localStoresActorSystem = ActorSystem("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
  val persistentActor = localStoresActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"Akka tutorial: $i"
  }
  persistentActor ! "print"
  persistentActor ! "snap"
  for (i <- 11 to 20) {
    persistentActor ! s"Akka tutorial: $i"
  }
}
