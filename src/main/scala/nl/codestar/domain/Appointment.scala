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
import ShardedAppointments._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}

object ShardedAppointments {
  def props() = Props(new ShardedAppointments)

  val shardName = "appointments"

  // Partial function to extract the entity id from the message
  // to send to the entity from the incoming message.
  // If the partial function does not match,
  // the message will be `unhandled`, i.e. posted as `Unhandled` messages on the event stream
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case a: AppointmentCommand ⇒ (a.id.toString, a)
  }

  // Partial function to extract the shard if from the message
  // From the docs:
  // "For a specific entity identifier the shard identifier must always be the same.
  // Otherwise the entity actor might accidentally be started in several places at the same time."
  // As a rule of thumb, the number of shards should be a factor ten greater than the planned maximum number of cluster nodes.
  val extractShardId: ShardRegion.ExtractShardId = {
    case a: AppointmentCommand ⇒ {
      val shard = a.id.hashCode() % 2
      println(s">>> Shard: $shard")
      shard.toString
    }
  }
}

class ShardedAppointments extends Actor with ActorLogging {

  ClusterSharding(context.system).start(
    typeName = shardName,
    entityProps = Appointment.props(),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )
  val shardRegion = ClusterSharding(context.system).shardRegion(shardName)

  override def receive: Receive = {
    case c: AppointmentCommand =>
      log.debug("Forwarding appointment command {}", c)
      shardRegion forward c
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
