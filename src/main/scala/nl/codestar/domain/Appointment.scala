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

package nl.codestar.domain

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object Appointments {
  def props() = Props(new Appointments)
}

class Appointments extends Actor with ActorLogging {
  override def receive: Receive = {
    case c: AppointmentCommand =>
      def getChild =
        context
          .child(c.id.toString)
          .getOrElse(context.actorOf(Appointment.props(), c.id.toString))

      log.debug("Forwarding appointment command {}", c)

      getChild forward c
  }
}

object Appointment {
  def props() = Props(new Appointment)
}

class Appointment extends PersistentActor with ActorLogging {
  override def persistenceId: String = self.path.name
  // TODO should be the state
  var app: AppointmentCreatedV3 = _

  override def receiveRecover: Receive = {
    case RecoveryCompleted ⇒ log.info("Recovery complete for {}", persistenceId)

    case e: AppointmentEvent =>
      log.debug("Recover event for id {}: {}", persistenceId, e)
      updateState(e)
  }

  override def receiveCommand: Receive = {
    // normally we would just accept the latest,
    // but for now let's accept them all
    case CreateAppointmentV1(id) =>
      persist(AppointmentCreatedV1(id)) { evt =>
        updateState(evt)
        sender() ! Done
      }

    case CreateAppointmentV2(id, subject, start, office) =>
      persist(AppointmentCreatedV2(id, subject, start, office)) { evt =>
        updateState(evt)
        sender() ! Done
      }

    case CreateAppointmentV3(id, subject, start, office, tags) =>
      persist(AppointmentCreatedV3(id, subject, start, office, tags)) { evt =>
        updateState(evt)
        sender() ! Done
      }

    case g: GetDetails =>
      sender() ! Some(
        GetDetailsResult(UUID.fromString(persistenceId), "Something", ZonedDateTime.now))
  }

  def updateState(evt: AppointmentEvent): Unit = evt match {
    case evt: AppointmentCreatedV3 =>
      log.info(s"Appointment created $evt")
      app = evt
    case _ =>
      log.error(
        "Ignoring event, class = {}, id = {} as this is an outdated version. Only used to get an entry in the event store to test upcasting",
        evt.getClass,
        evt.id
      )
  }
}
