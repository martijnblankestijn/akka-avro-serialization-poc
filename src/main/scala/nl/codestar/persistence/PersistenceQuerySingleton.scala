package nl.codestar.persistence

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.Logging.DebugLevel
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal.Identifier
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.Attributes.logLevels
import akka.stream.scaladsl.Sink.ignore
import akka.stream.scaladsl.{RestartSource, Source}

import scala.concurrent.duration._

object PersistenceQuerySingleton {
  def props(tag: String)(implicit materializer: ActorMaterializer) =
    Props(new PersistenceQuerySingleton(tag))

  def initializePersistenceQuery(system: ActorSystem, tag: String, role: String)(
      implicit mat: ActorMaterializer) =
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = PersistenceQuerySingleton.props(tag),
        "END",
        settings = ClusterSingletonManagerSettings(system).withRole(role)
      ),
      name = s"$tag-$role"
    )

  sealed trait Message
}

class PersistenceQuerySingleton(tag: String)(implicit materializer: ActorMaterializer)
    extends Actor
    with ActorLogging {
  private implicit val executionContext = context.system.dispatcher

  log.info(s"PersistenceQuerySingleton starting for tag $tag")

  val restartSource = RestartSource.withBackoff(
    minBackoff = 3.seconds,
    maxBackoff = 1.minutes,
    randomFactor = 0.2
  ) { () =>
    createSource
  }

  restartSource
    .log(tag, _.toString)
    .withAttributes(logLevels(onElement = DebugLevel))
    .recover {
      case t: RuntimeException =>
        log.warning("Exception while streaming the events: {}", t.getMessage)
        throw t
    }
    .runWith(ignore)

  private def createSource: Source[EventEnvelope, NotUsed] = {
    PersistenceQuery(context.system)
      .readJournalFor[CassandraReadJournal](Identifier)
      .eventsByTag(tag, Offset.noOffset)
  }

  override def receive: Receive = {
    case "End" => log.info("Ending ...")
    case x     => log.debug(s"New message: $x")
  }
}
