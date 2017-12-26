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

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{JavaUUID, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import nl.codestar.domain._

import scala.concurrent.duration._

object Server extends App {

  implicit val system           = ActorSystem("appointmentSystem")
  implicit val executionContext = system.dispatcher
  implicit val materializer     = ActorMaterializer()
  implicit val timeout          = Timeout(5 seconds)

  val logger       = Logging(system, getClass)
  val appointments = system.actorOf(Appointments.props(), "appointments")

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
              println("Creating three different entities to test the versions.")
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
              versions.foreach { appointments ! _ }
              "OK"
            }
          } ~
            get {
              complete("OK appointments")
            }
        }
    }

  Http().bindAndHandle(route, "localhost", 8080) map { binding =>
    logger.info(s"REST interface bound to ${binding.localAddress}")
  } recover {
    case ex =>
      logger.error(s"REST interface could not bind", ex.getMessage)
  }

}
