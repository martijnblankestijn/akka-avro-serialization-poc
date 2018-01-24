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

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{JavaUUID, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.jaeger.Jaeger
import kamon.prometheus.PrometheusReporter
import kamon.zipkin.ZipkinReporter
import nl.codestar.api.Server._
import nl.codestar.domain._
import nl.codestar.persistence.PersistenceQuerySingleton

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends App {
  Kamon.addReporter(new PrometheusReporter())
  Kamon.addReporter(new ZipkinReporter())
//  Kamon.addReporter(new Jaeger())
  def configName = if (args.length > 0) args(0) else "server-1"

  val server = new Server(ConfigFactory.load(configName))
  server.start
}

class API(appointments: ActorRef, log: LoggingAdapter)(implicit timeout: Timeout,
                                                       executionContext: ExecutionContext) {
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

}

class Server(config: Config) {
  implicit val system           = ActorSystem("appointmentSystem", config)
  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()
  implicit val timeout          = Timeout(5 seconds)

  private val serverPort          = config.getInt("server.http.port")
  private val serverName          = config.getString("server.http.host")
  private val log: LoggingAdapter = Logging(system, getClass)
  private val appointments        = system.actorOf(ShardedAppointments.props(), "appointments")
  private val api                 = new API(appointments, log)

  def start() = {
    if (hasEventProcessorRole(system.settings.config)) {
      log.info("Current node has role '{}'", eventProcessorRole)
      PersistenceQuerySingleton.initializePersistenceQuery(system,
                                                           "appointment",
                                                           eventProcessorRole)
    } else {
      log.info("Current node does NOT have role '{}", eventProcessorRole)
    }

    Http().bindAndHandle(api.route, serverName, serverPort) map { binding =>
      log.info(s"REST interface bound to ${binding.localAddress}")
    } recover {
      case ex =>
        log.error(s"REST interface could not bind", ex.getMessage)
    }
  }
}

object Server {
  private val eventProcessorRole = "event-processor"

  def hasEventProcessorRole(config: Config) =
    config.getStringList("akka.cluster.roles").contains(eventProcessorRole)
}
