package gn.akka.http.part2.high_level_server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

// 8
case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat: RootJsonFormat[Book] = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {

  import RouteDSLSpec._

  "A digital library backend" should {
    "return all the books in the library" in {
      // send an http request through the endpoint that you want to test
      // inspect the response
      Get("/api/book") ~> libraryRoute ~> check {
        // assertions
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting the query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2, "Itachi Uchiha", "Mangekyou Sharingan: The ultimate guide"))
      }
    }

    "return a book by calling the endpoint with the id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)

        strictEntity.contentType shouldBe ContentTypes.`application/json`
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "Itachi Uchiha", "Mangekyou Sharingan: The ultimate guide"))
      }
    }

    "insert a book into the database" in {
      val newBook = Book(5, "Thors", "The Viking")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        assert(books.contains(newBook))
        books should contain(newBook) // same as above
      }
    }

    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        // when checking rejections, you can't verify the status code, because you don't have it
//        status shouldBe StatusCodes.NotFound
        rejections should not be empty
        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }

    "return all the books written by a given author" in {
      Get("/api/book/author/Isaac%20Netero") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books.filter(_.author == "Isaac Netero")
      }
    }
  }
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

  var books = List(
    Book(1, "Isaac Netero", "Nen: Specialization technique"),
    Book(2, "Itachi Uchiha", "Mangekyou Sharingan: The ultimate guide"),
    Book(3, "Takamora Mamoru", "Boxing styles"),
    Book(4, "Eren Jaeger", "Titan transformation, 3rd edition")
  )

  /*
    GET /api/book - returns all book in the library
    GET /api/book/X - returns a single book with id X
    GET /api/book?id=X - same as previous
    POST /api/book - adds a new book to the library
    GET /api/book/author/X - returns all books written by author X
   */
  val libraryRoute: Route =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
        get {
          (path(IntNumber) | parameter('id.as[Int])) { id =>
            complete(books.find(_.id == id))
          } ~
            pathEndOrSingleSlash {
              complete(books)
            }
        } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
            complete(StatusCodes.OK)
          } ~
            complete(StatusCodes.BadRequest)
        }
    }
}
