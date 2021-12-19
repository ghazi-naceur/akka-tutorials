name := "akka-tutorials"

version := "0.1"

scalaVersion := "2.12.7"

// some libs are available in Bintray's JCenter
resolvers += Resolver.jcenterRepo

enablePlugins(SbtPlugin)

val akkaVersion = "2.6.13"
val akkaHttpVersion = "10.1.7"
lazy val postgresVersion = "42.2.2"
lazy val cassandraVersion = "0.91"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "com.pauldijou" %% "jwt-spray-json" % "2.1.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.pauldijou" %% "jwt-spray-json" % "2.1.0",
  "org.scalatest" %% "scalatest" % "3.0.5",
  // Cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,
  // JDBC with PostgresSQL
  "org.postgresql" % "postgresql" % postgresVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.4.0",
  // Local LevelDB stores
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  // Google Protocol Buffer
  "com.google.protobuf" % "protobuf-java" % "3.6.1"
)
