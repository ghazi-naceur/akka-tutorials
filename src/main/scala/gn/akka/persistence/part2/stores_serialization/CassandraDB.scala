package gn.akka.persistence.part2.stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

// 3
object CassandraDB extends App {

  val cassandraActorSystem = ActorSystem("cassandraSystem", ConfigFactory.load().getConfig("cassandraDemo"))
  val persistentActor = cassandraActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"Akka tutorial: $i"
  }
  persistentActor ! "print"
  persistentActor ! "snap"
  for (i <- 11 to 20) {
    persistentActor ! s"Akka tutorial: $i"
  }

  /*
    After running this example, it will automatically create:
      - 2 keyspaces with the name "akka" and "akka_snapshot":
      - tables inside "akka" and "akka_snapshot", with names:
          + akka.messages
          + akka.metadata
          + akka.tag_scanning
          + akka.tag_views
          + akka.tag_write_progress
          + akka_snapshot.snapshots

      Run these 2 commands for more info:
         desc akka;
         desc akka_snapshot;
         select * from akka.messages;
         select * from akka_snapshot.snapshots;
   */
}
