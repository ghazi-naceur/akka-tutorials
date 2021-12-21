package gn.akka.persistence.part2.stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

// 2
object PostgresSQL extends App {

  val postgresActorSystem = ActorSystem("postgresSystem", ConfigFactory.load().getConfig("postgresDemo"))
  val persistentActor = postgresActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"Akka tutorial: $i"
  }
  persistentActor ! "print"
  persistentActor ! "snap"
  for (i <- 11 to 20) {
    persistentActor ! s"Akka tutorial: $i"
  }

  /*
    After executing 'select * from public.journal;', you'll notice that the 'message' column is set as bytes, so it's not
    readable, and this is caused by the the Java Serialization
    'select * from public.snapshot;' ==> a snapshot is stored
   */
}
