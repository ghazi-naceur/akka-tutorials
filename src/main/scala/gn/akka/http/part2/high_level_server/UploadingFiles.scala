package gn.akka.http.part2.high_level_server

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.{ByteString, Timeout}

import java.io.File
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 10
object UploadingFiles extends App {
  implicit val system: ActorSystem = ActorSystem("UploadingFiles")
  import akka.stream.Materializer.matFromSystem
  import akka.http.scaladsl.server.Directives._
  import system.dispatcher
  implicit val timeout: Timeout = Timeout(2 seconds)
  import spray.json._

  val filesRoute = {
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |     <form action="http://localhost:8010/upload" method="post" enctype="multipart/form-data">
            |       <input type="file" name="myFile">
            |       <button type="submit">Upload</button>
            |     </form>
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
    } ~
      (path("upload") & extractLog) { log =>
        // handle uploading files
        // HTTP Request that are responsible for uploading files, are sending special HTTP entities called: 'multipart/form-data':
        entity(as[Multipart.FormData]) { formData =>
          // formData contains the file payload
          val partsSource: Source[Multipart.FormData.BodyPart, Any] = formData.parts
          val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] =
            Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
              if (bodyPart.name == "myFile") {
                // create a file to dump the parts in
                val filename =
                  "src/main/resources/download/" + bodyPart.filename.getOrElse("tempFile_" + System.currentTimeMillis())

                val file = new File(filename)
                log.info(s"Writing to file: '$filename'")

                val fileContentsSource: Source[ByteString, _] = bodyPart.entity.dataBytes
                val fileContentsSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(file.toPath)

                fileContentsSource.runWith(fileContentsSink) // dumping content to file
              }
            }
          val writeOperationFuture = partsSource.runWith(filePartsSink)
          onComplete(writeOperationFuture) {
            case Success(_)  => complete("File uploaded")
            case Failure(ex) => complete(s"File failed to upload due to: $ex")
          }
        }
      }
  }

  Http().bindAndHandle(filesRoute, "localhost", 8010)
  /*
      Open browser http://localhost:8010
   */
}
