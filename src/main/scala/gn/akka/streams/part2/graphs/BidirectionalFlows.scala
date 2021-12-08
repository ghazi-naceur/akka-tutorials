package gn.akka.streams.part2.graphs

import akka.actor.ActorSystem
import akka.stream.{BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

// 5
object BidirectionalFlows extends App {
  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")
  import akka.stream.Materializer.matFromSystem

  // Cryptography
  def encrypt(n: Int)(string: String): String = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String): String = string.map(c => (c - n).toChar)
  println(encrypt(3)("Isaac"))
  println(decrypt(3)("Lvddf"))

  // Bidirectional flow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3))) // == (element => encrypt(3)(element))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3))) // == (element => decrypt(3)(element))

//    BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    // or simply:
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("Isaac", "Netero", "is", "an", "old", "man")
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val unencryptedSourceShape = builder.add(unencryptedSource)
    val encryptedSourceShape = builder.add(encryptedSource)
    val bidi = builder.add(bidiCryptoStaticGraph)
    val encryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"Encrypted: $string")))
    val decryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"decrypted: $string")))

//    unencryptedSourceShape ~> bidi.in1; bidi.out1 ~> encryptedSinkShape
//    encryptedSourceShape ~> bidi.in2; bidi.out2 ~> decryptedSinkShape
    // or simply:
    unencryptedSourceShape ~> bidi.in1; bidi.out1 ~> encryptedSinkShape
    decryptedSinkShape <~ bidi.out2; bidi.in2 <~ encryptedSourceShape

    ClosedShape
  })

  cryptoBidiGraph.run()
}
