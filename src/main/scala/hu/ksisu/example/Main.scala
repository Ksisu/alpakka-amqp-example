package hu.ksisu.example

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.alpakka.amqp.scaladsl.AmqpSink
import akka.stream.alpakka.amqp.{
  AmqpCachedConnectionProvider,
  AmqpConnectionProvider,
  AmqpUriConnectionProvider,
  AmqpWriteSettings,
  QueueDeclaration
}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.{ExecutionContext, Future}

object Main extends App {
  private implicit lazy val system           = ActorSystem("example-system")
  private implicit lazy val executionContext = system.dispatcher

  private implicit val queue   = new AmqpHelper()
  private implicit val service = new Service()
  private val api              = new Api()

  Http().bindAndHandle(api.route, "0.0.0.0", 9000)
}

class AmqpHelper(implicit as: ActorSystem, ec: ExecutionContext) {
  private val connection: AmqpConnectionProvider = {
    AmqpCachedConnectionProvider(
      AmqpUriConnectionProvider("amqp://guest:guest@localhost")
    )
  }
  private val queueName        = "queue-example-" + System.currentTimeMillis()
  private val queueDeclaration = QueueDeclaration(queueName)

  private val queue: SourceQueueWithComplete[ByteString] = {
    val amqpSink: Sink[ByteString, Future[Done]] = {
      AmqpSink.simple(
        AmqpWriteSettings(connection)
          .withRoutingKey(queueName)
          .withDeclaration(queueDeclaration)
      )
    }
    Source
      .queue[ByteString](100, OverflowStrategy.fail)
      .toMat(amqpSink)(Keep.left)
      .run()
  }

  def sendToQueue[T](key: String, data: T)(implicit w: JsonWriter[T]): Future[Boolean] = {
    val jsonMsg = JsObject(
      "key"  -> JsString(key),
      "data" -> data.toJson
    )
    val msg = ByteString(jsonMsg.compactPrint)
    queue.offer(msg).map {
      case Enqueued => true
      case _        => false
    }
  }

}

class Service(implicit ec: ExecutionContext, amqp: AmqpHelper) {

  def sayHello(who: String): Future[String] = {
    amqp.sendToQueue("hello", who).map { _ =>
      s"Hello $who!\n"
    }
  }

  def sayBye(who: String): Future[String] = {
    amqp.sendToQueue("bye", who).map { _ =>
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
