package hu.ksisu.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.{ExecutionContext, Future}

object Main extends App {
  private implicit lazy val system           = ActorSystem("example-system")
  private implicit lazy val executionContext = system.dispatcher

  private implicit val service = new Service()
  private val api              = new Api()

  Http().bindAndHandle(api.route, "0.0.0.0", 9000)
}

class Service(implicit ec: ExecutionContext) {
  def sayHello(who: String): Future[String] = {
    Future {
      s"Hello $who!\n"
    }
  }

  def sayBye(who: String): Future[String] = {
    Future {
      s"Bye $who!\n"
    }
  }
}

class Api(implicit service: Service) {
  val route: Route =
    path("hello" / Segment) { who =>
      post {
        onSuccess(service.sayHello(who))(complete(_))
      }
    } ~ path("bye" / Segment) { who =>
      post {
        onSuccess(service.sayBye(who))(complete(_))
      }
    }
}
