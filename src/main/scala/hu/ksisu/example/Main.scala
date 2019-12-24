package hu.ksisu.example

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.alpakka.amqp.scaladsl.{AmqpSink, AmqpSource}
import akka.stream.alpakka.amqp.{
  AmqpCachedConnectionProvider,
  AmqpConnectionProvider,
  AmqpUriConnectionProvider,
  AmqpWriteSettings,
  NamedQueueSourceSettings,
  QueueDeclaration
}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object Main extends App {
  private implicit lazy val logger           = LoggerFactory.getLogger("EXAMPLE-API")
  private implicit lazy val system           = ActorSystem("example-system")
  private implicit lazy val executionContext = system.dispatcher

  private implicit val queue   = new AmqpHelper()
  private implicit val service = new Service()
  private val api              = new Api()

  Http().bindAndHandle(api.route, "0.0.0.0", 9000)
}

object WorkerMain extends App {
  private implicit lazy val logger           = LoggerFactory.getLogger("EXAMPLE-WORKER")
  private implicit lazy val system           = ActorSystem("example-worker-system")
  private implicit lazy val executionContext = system.dispatcher

  private implicit val queue   = new AmqpHelper()
  private implicit val service = new WorkerService()
  service.start()
}

class AmqpHelper(implicit as: ActorSystem, ec: ExecutionContext, logger: Logger) {
  private val connection: AmqpConnectionProvider = {
    AmqpCachedConnectionProvider(
      AmqpUriConnectionProvider("amqp://guest:guest@localhost")
    )
  }
  private val queueName        = "queue-example"
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
    logger.info(s"Send message to queue: ${msg.utf8String}")
    queue.offer(msg).map {
      case Enqueued => true
      case _        => false
    }
  }

  def getSource(): Source[(String, JsValue), NotUsed] = {
    AmqpSource
      .atMostOnceSource(
        NamedQueueSourceSettings(connection, queueName)
          .withDeclaration(queueDeclaration)
          .withAckRequired(false),
        bufferSize = 10
      )
      .mapConcat { readResult =>
        val msg = readResult.bytes.utf8String
        logger.info(s"Message received from queue: $msg")
        Try(msg.parseJson)
          .collect {
            case jsonMsg: JsObject => jsonMsg.getFields("key", "data")
          }
          .collect {
            case Seq(JsString(key), data) => key -> data
          }
          .map(List(_))
          .getOrElse(List.empty)
      }
  }

}

class Service(implicit ec: ExecutionContext, amqp: AmqpHelper, logger: Logger) {

  def sayHello(who: String): Future[String] = {
    logger.info(s"SayHello: $who")
    amqp.sendToQueue("hello", who).map { _ =>
      s"Hello $who!\n"
    }
  }

  def sayBye(who: String): Future[String] = {
    logger.info(s"SayBye: $who")
    amqp.sendToQueue("bye", who).map { _ =>
      s"Bye $who!\n"
    }
  }
}

class WorkerService(implicit as: ActorSystem, amqp: AmqpHelper, logger: Logger) {

  private val stream = {
    amqp
      .getSource()
      .collect {
        case ("hello", JsString(value)) => logger.info(s"Msg processed - [hello]: $value")
        case ("bye", JsString(value))   => logger.info(s"Msg processed - [bye]: $value")
      }
      .to(Sink.ignore)
  }

  def start(): Unit = {
    stream.run()
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
