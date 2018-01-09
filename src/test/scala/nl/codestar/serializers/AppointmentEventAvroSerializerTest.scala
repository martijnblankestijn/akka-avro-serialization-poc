package nl.codestar.serializers

import java.io.NotSerializableException
import java.time.ZonedDateTime
import java.util.UUID
import java.util.UUID.randomUUID

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.testkit.TestKit
import nl.codestar.domain._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import org.scalatest.{FunSpecLike, MustMatchers}

class AppointmentEventAvroSerializerTest
    extends TestKit(ActorSystem("MySpec"))
    with FunSpecLike
    with MustMatchers {
  val serializer               = new AppointmentEventAvroSerializer(this.system.asInstanceOf[ExtendedActorSystem])
  val defaultSubjectFromSchema = "DBZ_KW"
  val defaultDateFromSchema    = ZonedDateTime.parse("2017-12-31T12:34:45Z")

  val validEvents = Table(
    ("Schema", "Event"),
    ("appointment-1", AppointmentCreatedV1(randomUUID())),
    ("appointment-2",
     AppointmentCreatedV2(randomUUID(),
                          "subject",
                          ZonedDateTime.now(),
                          Some(BranchOfficeV1("12345")))),
    ("appointment-3",
     AppointmentCreatedV3(randomUUID(),
                          "subject",
                          ZonedDateTime.now(),
                          Some(BranchOfficeV2("12345", Some(randomUUID()))),
                          Set("A", "B")))
  )

  describe("manifest splitter") {
    it("should generate a correct manifest") {
      forAll(validEvents) { (schema, event) =>
        serializer.manifest(event) must be(schema)
      }
    }

    it("should return empty quotes for unsupported instances") {
      an[NotSerializableException] must be thrownBy serializer.manifest(List())
    }
  }

  describe("toBinary") {}

  describe("fromBinary") {
    it("should upcast the event to latest version") {
      forAll(validEvents) { (schema, event) =>
        val bytes = serializer.toBinary(event)
        serializer.fromBinary(bytes, schema) mustBe an[AppointmentCreatedV3]
      }
    }

    it("should throw an exception with an invalid manifest") {
      an[NotSerializableException] must be thrownBy
        serializer.fromBinary(null, "noversion")
    }

    it("should throw an exception with an unsupported type or version") {
      an[NotSerializableException] must be thrownBy
        serializer.fromBinary(null, "appointment-99")
      an[NotSerializableException] must be thrownBy
        serializer.fromBinary(null, "unknown-1")
    }
  }
}
