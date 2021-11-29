package gn.akka.essentials.part1.actor

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

// 9
object IntroAkkaConfig {

  class SimpleLoggingActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  def main(args: Array[String]): Unit = {

    /**
      * 1- Inline configuration
      */
    val configString =
      """
        | akka {
        |  loglevel = "DEBUG"
        | }
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val systemWithStringConf = ActorSystem("configdemo", ConfigFactory.load(config))
    // config object is loaded in the ActorSystem

    val loggingActor = systemWithStringConf.actorOf(Props[SimpleLoggingActor])
    loggingActor ! "to be logged"
    /*
    We'll notice some additional debug logging lines added in the console, because we specified 'loglevel = DEBUG' inside
    the configuration :

    [DEBUG] [11/29/2021 13:59:02.362] [main] [EventStream(akka://configdemo)] logger log1-Logging$DefaultLogger started
    [DEBUG] [11/29/2021 13:59:02.363] [main] [EventStream(akka://configdemo)] Default Loggers started
    [INFO] [11/29/2021 13:59:02.402] [configdemo-akka.actor.default-dispatcher-4] [akka://configdemo/user/$a] to be logged
     */

    /**
      * 2- Configuration file src/main/resources/application.conf
      */
    val systemWithFileConf = ActorSystem("configdemo2")
    // automatically load conf from 'src/main/resources/application.conf 'src/main/resources/application.conf''

    val actorWithFileConf = systemWithFileConf.actorOf(Props[SimpleLoggingActor])
    actorWithFileConf ! "something else"

    /**
      * 3- Separate config in the same file
      */
    val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
    val specialConfigSystem = ActorSystem("specialConfigDemo", specialConfig)
    val actorWithSpecialConfig = specialConfigSystem.actorOf(Props[SimpleLoggingActor])
    actorWithSpecialConfig ! "yet another something else"

    /**
      * 4- Separate config in another file
      */
    val configInSepFile = ConfigFactory.load("secret-folder/secretConfiguration.conf")
    println("content of 'akka.loglevel': " + configInSepFile.getString("akka.loglevel"))
    val configSystemInSepFile = ActorSystem("sepConfigDemo", configInSepFile)
    val actorWithSpecialConfigInSepFile = configSystemInSepFile.actorOf(Props[SimpleLoggingActor])
    actorWithSpecialConfigInSepFile ! "indeed another something else"

    /**
      * 5- Different file formats: CFG, JSON and Properties
      */
    val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
    println(s"content of 'akka.loglevel': ${jsonConfig.getString("akka.loglevel")}")

    val propsConfig = ConfigFactory.load("properties/propsConfig.properties")
    println(s"content of 'akka.loglevel': ${propsConfig.getString("akka.loglevel")}")
  }
}
