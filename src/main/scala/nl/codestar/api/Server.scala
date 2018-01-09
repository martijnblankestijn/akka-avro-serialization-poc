/*
 * Copyright 2017 Martijn Blankestijn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.codestar.api

import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.Logging
import akka.event.Logging.DebugLevel
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{JavaUUID, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal.Identifier
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.Attributes.logLevels
import akka.stream.scaladsl.Sink.ignore
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import nl.codestar.api.PersistenceQuerySingleton.End
import nl.codestar.domain._

import scala.concurrent.duration._

object Server extends App {
  val configName = if (args.length > 0) args(0) else "server-1"

  private val config            = ConfigFactory.load(configName)
  implicit val system           = ActorSystem("appointmentSystem", config)
  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()
  implicit val timeout          = Timeout(5 seconds)

  private val serverName = config.getString("server.http.host")
  val log                = Logging(system, getClass)
  val appointments       = system.actorOf(Appointments.props(), "appointments")

  val route: Route =
    pathPrefix("appointments") {
      pathPrefix(JavaUUID) { uuid =>
        get {
          complete {
            (appointments ? GetDetails(uuid))
              .mapTo[Option[GetDetailsResult]]
              .map(_.map(r => s"${r.id} ${r.subject} ${r.start}")
                .getOrElse("NOT FOUND"))
          }
        }
      } ~
        pathEnd {
          post {
            complete {
              val versions = List(
                CreateAppointmentV1(UUID.randomUUID()),
                CreateAppointmentV2(UUID.randomUUID(),
                                    "subject 2",
                                    ZonedDateTime.now,
                                    Some(BranchOfficeV1("12345"))),
                CreateAppointmentV3(UUID.randomUUID(),
                                    "subject 3",
                                    ZonedDateTime.now,
                                    Some(BranchOfficeV2("54321", Some(UUID.randomUUID()))))
              )
              versions.foreach { v =>
                (appointments ? v).map(_ => log.debug(s"$v is done."))
              }
              "OK"
            }
          } ~
            get {
              complete("OK appointments")
            }
        }
    }

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = PersistenceQuerySingleton.props("appointment"),
       "END", //      terminationMessage = End, // NOT SURE IF I AM GOING TO USE THIS ONE
      settings = ClusterSingletonManagerSettings(system).withRole("event-processor")
    ),
    name = "eventProcessor"
  )
  private val serverPort = config.getInt("server.http.port")

  Http().bindAndHandle(route, serverName, serverPort) map { binding =>
    log.info(s"REST interface bound to ${binding.localAddress}")
  } recover {
    case ex =>
      log.error(s"REST interface could not bind", ex.getMessage)
  }

}

object PersistenceQuerySingleton {
  def props(tag: String)(implicit materializer: ActorMaterializer) =
    Props(new PersistenceQuerySingleton(tag))

  sealed trait Message

  // TODO this should be Avro serializable (or just implement ??)
  case object End extends Message

}

class PersistenceQuerySingleton(tag: String)(implicit materializer: ActorMaterializer)
    extends Actor
    with ActorLogging {
  private implicit val executionContext = context.system.dispatcher

  log.info(s"PersistenceQuerySingleton starting for tag $tag")

  PersistenceQuery(context.system)
    .readJournalFor[CassandraReadJournal](Identifier)
    .eventsByTag(tag, Offset.noOffset)
    .map(e => {println(s"ENVELOPE: $e");e})
    .log(tag, _.toString)
    .withAttributes(logLevels(onElement = DebugLevel))
    .runWith(ignore)

  override def receive: Receive = {
    case End => log.info("Ending ...")
    case x   => log.debug(s"New message: $x")
  }
}
