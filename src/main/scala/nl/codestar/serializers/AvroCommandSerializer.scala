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

package nl.codestar.serializers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, NotSerializableException}
import java.time.ZonedDateTime

import akka.actor.ExtendedActorSystem
import akka.event.{LogSource, Logging}
import akka.serialization.Serializer
import com.sksamuel.avro4s._
import nl.codestar.domain._
import nl.codestar.serializers.AvroCommandSerializer._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.apache.avro.Schema.Type.STRING

import scala.reflect.ClassTag

object AvroCommandSerializer {
  def writeBinary[T: SchemaFor: ToRecord](t: T): Array[Byte] = {
    val output = new ByteArrayOutputStream
    val os     = AvroBinaryOutputStream(output)(implicitly[SchemaFor[T]], implicitly[ToRecord[T]])
    os.write(t)
    os.close()
    output.toByteArray
  }

  def readBinary[T: SchemaFor: FromRecord](bytes: Array[Byte]): T = 
    getRecord(new AvroBinaryInputStream[T](new ByteArrayInputStream(bytes)))
  

  def getRecord[T](is: AvroBinaryInputStream[T]): T = {
    def handleError(t: Throwable): T = {
      is.close
      throw new NotSerializableException(s"Error while serializing: ${t.getMessage}")
    }
    def handleSuccss(t: T): T = {
      is.close()
      t
    }
    is.tryIterator().next().fold(handleError, handleSuccss)
  }

  implicit object DateTimeToSchema extends ToSchema[ZonedDateTime] {
    override val schema: Schema = Schema.create(STRING)
  }

  implicit object DateTimeToValue extends ToValue[ZonedDateTime] {
    override def apply(value: ZonedDateTime): String = value.toString
  }

  implicit object DateTimeFromValue extends FromValue[ZonedDateTime] {
    override def apply(value: Any, field: Field = null): ZonedDateTime =
      ZonedDateTime.parse(value.toString)
  }

  private def throwNoSerializerFound(clazz: Class[_]) =
    throw new NotSerializableException(s"No mapping found for serializing [$clazz]")

  private def throwManifestRequired(clazz: Class[_]) =
    throw new NotSerializableException(s"Manifest required for [$clazz]")
}

class AvroCommandSerializer(system: ExtendedActorSystem) extends Serializer {
  val log = {
    implicit val logSource = new LogSource[AvroCommandSerializer] {
      override def genString(t: AvroCommandSerializer) =
        "AppointmentAvroSerializer"
    }
    Logging(system, this)
  }

  private val classes: Map[Class[_], (AnyRef => Array[Byte], Array[Byte] => AnyRef)] = Map(
    create[GetDetails],
    create[GetDetailsResult],
    create[CreateAppointmentV1],
    create[CreateAppointmentV2],
    create[CreateAppointmentV3]
  )

  private def create[T: SchemaFor: ToRecord: FromRecord: ClassTag]: 
  (Class[_], (AnyRef => Array[Byte], Array[Byte] => AnyRef)) = {
    (implicitly[ClassTag[T]].runtimeClass,
     (anyRef => writeBinary(anyRef.asInstanceOf[T])(implicitly[SchemaFor[T]], implicitly[ToRecord[T]]),
      bytes => readBinary(bytes)(implicitly[SchemaFor[T]], implicitly[FromRecord[T]]).asInstanceOf[AnyRef]))
  }

  override def identifier: Int = 990001

  override def toBinary(theObject: AnyRef): Array[Byte] = {
    log.debug("toBinary: [{}]", theObject)
    classes
      .getOrElse(theObject.getClass, throwNoSerializerFound(theObject.getClass))
      ._1(theObject)
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val clazz = classFromManifest(manifest)
    log.debug("fromBinary: manifest = [{}]", clazz)

    val t = classes
      .getOrElse(clazz, throwNoSerializerFound(clazz))
      ._2(bytes)
    log.debug("fromBinary: [{}]", t)
    t
  }

  private def classFromManifest(manifest: Option[Class[_]]) =
    manifest.getOrElse(throwManifestRequired(this.getClass))

  override def includeManifest: Boolean = true
}
