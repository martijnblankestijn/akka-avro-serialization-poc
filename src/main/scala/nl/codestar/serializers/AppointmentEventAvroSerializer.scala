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

import java.io._

import akka.actor.ExtendedActorSystem
import akka.event.{LogSource, Logging}
import akka.serialization.SerializerWithStringManifest
import com.sksamuel.avro4s._
import nl.codestar.domain.{
  AppointmentCreatedV1,
  AppointmentCreatedV2,
  AppointmentCreatedV3,
  AppointmentEvent
}
import nl.codestar.serializers.AppointmentEventAvroSerializer._
import nl.codestar.serializers.AvroCommandSerializer.{writeBinary, _}
import nl.codestar.serializers.MyAvroInputStream.binary
import org.apache.avro.Schema

object AppointmentEventAvroSerializer {
  val parser = new Schema.Parser

  private def parse(inputStream: InputStream) =
    try {
      parser.parse(inputStream)
    } finally {
      inputStream.close()
    }

  private def parse(file: String): Schema = {
    Option(classOf[AppointmentEventAvroSerializer].getResourceAsStream(file))
      .map(parser.parse)
      .getOrElse(throw new NotSerializableException(s"File [$file] not found."))
  }

  private val AppointmentManifest = "appointment"
  private val currentVersion      = "3"
  private val supportedVersions
    : Map[Class[_], (String, ToRecord[_ <: AppointmentEvent], SchemaFor[_ <: AppointmentEvent])] =
    Map(
      classOf[AppointmentCreatedV1] ->
        ("1", ToRecord[AppointmentCreatedV1], SchemaFor[AppointmentCreatedV1]),
      classOf[AppointmentCreatedV2] ->
        ("2", ToRecord[AppointmentCreatedV2], SchemaFor[AppointmentCreatedV2]),
      classOf[AppointmentCreatedV3] ->
        ("3", ToRecord[AppointmentCreatedV3], SchemaFor[AppointmentCreatedV3])
    )
  val eventSchemas: Map[String, Schema] =
    supportedVersions.values.map(t => (t._1, parse(s"/appointmentevent-v${t._1}.json"))).toMap

  val typeVersionRegex = "([A-Za-z]*)-(\\d)*$".r

  private def read[T: SchemaFor: FromRecord](inputStream: InputStream,
                                             writerSchema: Schema,
                                             readerSchema: Schema): T =
    getRecord(binary[T](inputStream, writerSchema, readerSchema))

  private def noSerializerSupport(o: AnyRef) =
    throw new NotSerializableException(s"No serializer support for [$o]")
}

class AppointmentEventAvroSerializer(system: ExtendedActorSystem)
    extends SerializerWithStringManifest {
  val log = {
    implicit val logSource = new LogSource[AppointmentEventAvroSerializer] {
      override def genString(t: AppointmentEventAvroSerializer) =
        "AppointmentEventAvroSerializer"
    }
    Logging(system, this)
  }

  override def identifier: Int = 990000
  override def manifest(o: AnyRef): String =
    supportedVersions
      .get(o.getClass)
      .map { case (version, _, _) => s"$AppointmentManifest-$version" }
      .getOrElse(noSerializerSupport(o))

  override def toBinary(o: AnyRef): Array[Byte] = {
// TODO would like to do something like this, but cannot get the types aligned
//    supportedVersions.get(o.getClass)
//      .map { case (_, toRecord, schemaFor) => writeBinary(o)(schemaFor, toRecord) }
//      .getOrElse(noSerializerSupport(o))
    o match {
      // TODO Get rid of the duplication
      // On the other hand, normally an application would just have one version to persist, only
      // different versions to read!!
      case x: AppointmentCreatedV1 => writeBinary[AppointmentCreatedV1](x)
      case x: AppointmentCreatedV2 => writeBinary[AppointmentCreatedV2](x)
      case x: AppointmentCreatedV3 => writeBinary[AppointmentCreatedV3](x)
      case _                       => noSerializerSupport(o)
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case typeVersionRegex(typ, writerSchema) =>
        typ match {
          case AppointmentManifest if eventSchemas contains writerSchema =>
            log.debug("Reader, writer schema [{}]: [{}]", currentVersion, writerSchema)
            read[AppointmentCreatedV3](new ByteArrayInputStream(bytes),
                                       eventSchemas(writerSchema),
                                       eventSchemas(currentVersion))
          case _ =>
            throw new NotSerializableException(s"No serializer found for manifest [$manifest]")
        }
      case _ =>
        throw new NotSerializableException(s"Unsupported pattern for manifest [$manifest]")
    }
}
