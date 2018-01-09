package nl.codestar.serializers

import java.io.NotSerializableException
import java.time.ZonedDateTime
import java.util.UUID.randomUUID

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.serialization.SerializationExtension
import akka.testkit.TestKit
import nl.codestar.domain._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpecLike, MustMatchers}

class AvroCommandSerializerTest
    extends TestKit(ActorSystem("MySpec"))
    with FunSpecLike
    with MustMatchers {

  val serialization = SerializationExtension(system)
  val validObject   = CreateAppointmentV1(randomUUID())

  val validCommands = Table(
    "command",
    GetDetails(randomUUID()),
    GetDetailsResult(randomUUID(), "Result subject", ZonedDateTime.now),
    validObject,
    CreateAppointmentV2(randomUUID(),
                        "V2 subject",
                        ZonedDateTime.now,
                        Some(BranchOfficeV1("54321"))),
    CreateAppointmentV3(randomUUID(),
                        "V2 subject",
                        ZonedDateTime.now,
                        Some(BranchOfficeV2("12345", Some(randomUUID()))))
  )

  describe("manifest") {
    it("should always return true") {
      new AvroCommandSerializer(system.asInstanceOf[ExtendedActorSystem]).includeManifest must be(
        true)
    }
  }

  describe("serializing") {
    it("should reject serializing unsupported commands") {
      val serializer = new AvroCommandSerializer(system.asInstanceOf[ExtendedActorSystem])
      an[NotSerializableException] must be thrownBy serializer.toBinary(CaseTestClass(5))
    }

    it("should serialize all supported commands") {
      forAll(validCommands) { command =>
        serialization.findSerializerFor(command).toBinary(command).length must be > 0
      }
    }
  }

  describe("deserialize") {
    val serializer = serialization.findSerializerFor(validObject)
    val bytes      = serializer.toBinary(validObject)

    it("should reject commands without manifest") {
      an[NotSerializableException] must be thrownBy serializer.fromBinary(bytes, None)
    }

    it("should reject unparseable comands") {
      an[NotSerializableException] must be thrownBy serializer.fromBinary(Array.empty[Byte],
                                                                          Some(classOf[GetDetails]))
    }

    it("should serialize and deserialize the same object") {
      forAll(validCommands) { command =>
        val serializer = serialization.findSerializerFor(command)
        val bytes      = serializer.toBinary(command)

        serializer.fromBinary(bytes, Some(command.getClass)) must be(command)
      }
    }
  }
}

case class CaseTestClass(i: Int)
