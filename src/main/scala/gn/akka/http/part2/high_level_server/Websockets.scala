package gn.akka.http.part2.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.{CompactByteString, Timeout}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 9
object Websockets extends App {
  implicit val system: ActorSystem = ActorSystem("Websockets")
  import akka.stream.Materializer.matFromSystem
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  // Message types: TextMessage vs BinaryMessage

  val textMessage = TextMessage(Source.single("Hello via a text message"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("Hello via a binary message")))

  val html =
    """
      |<!DOCTYPE html>
      |<html lang="en">
      |<head>
      |    <meta charset="UTF-8">
      |    <title>Websockets example</title>
      |    <script>
      |        var exampleSocket = new WebSocket("ws://localhost:8050/greeter");
      |        console.log("Starting websocket...");
      |        exampleSocket.onmessage = function(event) {
      |            var newChild = document.createElement("div");
      |            newChild.innerText = event.data;
      |            document.getElementById("1").appendChild(newChild);
      |        };
      |        
      |        exampleSocket.onopen = function(event) {
      |            exampleSocket.send("Socket seems to be open...");
      |        };
      |        
      |        exampleSocket.send("Socket says: Hello server!");
      |    </script>
      |</head>
      |<body>
      |    Starting WebSocket...
      |    <div id="1">
      |    </div>
      |</body>
      |</html>
      |""".stripMargin

  def websocketFlow: Flow[Message, Message, Any] =
    Flow[Message].map {
      case tm: TextMessage =>
        TextMessage(Source.single("Server says back: ") ++ tm.textStream ++ Source.single(" !"))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore) // to avoid leaking resource, we need to drain the binary message
        TextMessage(Source.single("Server received a binary massage."))
    }

  case class SocialPost(owner: String, content: String)

  val socialFeed = Source(
    List(
      SocialPost("Isaac", "The war against the Chimera Ants has began"),
      SocialPost("Shisui", "Preventing the chaos"),
      SocialPost("Isaac", "A sacrifice must be done")
    )
  )

  val socialMessages = socialFeed
    .throttle(1, 2 seconds)
    .map(socialPost => TextMessage(s"'${socialPost.owner}' said: '${socialPost.content}'"))

  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(Sink.foreach[Message](println), socialMessages)
  /*
  'fromSinkAndSource': Flow from unlinked source and sink

  [Message, Message, Any] :
      Message: from the client and will be send to 'Sink.foreach'
      Message: socialMessages
      Any: No specific materialized value
   */

  /*
    Once you run the app, the instruction 'exampleSocket.send("Socket says: Hello server!");' won't send the message to
    because the websocket is not open yet (see the browser console after starting the app).
    The only message sent to the server is: 'exampleSocket.send("Socket seems to be open...");' (seen on the browser)
   */

  val websocketRoute =
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
    } ~
      path("greeter") {
        handleWebSocketMessages(socialFlow)
      }

  Http().bindAndHandle(websocketRoute, "localhost", 8050)
}
