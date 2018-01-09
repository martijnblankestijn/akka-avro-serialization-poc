package nl.codestar.domain

import java.io.{File, FileInputStream}
import java.time.ZonedDateTime
import java.util.UUID
import java.util.UUID.randomUUID

import com.sksamuel.avro4s._
import nl.codestar.serializers.MyAvroInputStream
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.apache.avro.file.SeekableFileInput
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}

class AppointmentTest extends FunSpec with Matchers {
  implicit object DateTimeToSchema extends ToSchema[ZonedDateTime] {
    override val schema = Schema.create(Schema.Type.STRING)
  }

  implicit object DateTimeToValue extends ToValue[ZonedDateTime] {
    override def apply(value: ZonedDateTime): String = value.toString
  }

  implicit object DateTimeFromValue extends FromValue[ZonedDateTime] {
    override def apply(value: Any, field: Field = null): ZonedDateTime =
      ZonedDateTime.parse(value.toString)
  }

  describe("serialization") {
    val fileV1 = new File("appointment-v1.avro")
    val fileV2 = new File("appointment-v2.avro")
    val fileV3 = new File("appointment-v3.avro")

    val schemaV1: Schema = readSchema("appointmentevent-v1.json")
    val schemaV2: Schema = readSchema("appointmentevent-v2.json")
    val schemaV3: Schema = readSchema("appointmentevent-v3.json")

    val expectedV1 = Seq(AppointmentCreatedV1(randomUUID()), AppointmentCreatedV1(randomUUID()))
    val expectedV2 = Seq(
      AppointmentCreatedV2(randomUUID(), "DBZ_KW", ZonedDateTime.now, None),
      AppointmentCreatedV2(randomUUID(),
                           "Something",
                           ZonedDateTime.now,
                           Some(BranchOfficeV1("12345")))
    )
    val expectedV3 = Seq(
      AppointmentCreatedV3(randomUUID(),
                           "DBZ_KW",
                           ZonedDateTime.now,
                           Some(BranchOfficeV2("12345"))),
      AppointmentCreatedV3(randomUUID(),
                           "Something",
                           ZonedDateTime.now,
                           Some(BranchOfficeV2("12345", Some(randomUUID()))),
                           Set("A", "B"))
    )

    it("should log the schema") {
      println(AvroSchema[CreateAppointmentV1])
      println(AvroSchema[CreateAppointmentV2])
      println(AvroSchema[AppointmentCreatedV1])
      println(AvroSchema[AppointmentCreatedV2])
      println(AvroSchema[AppointmentCreatedV3])
      println(AvroSchema[GetDetails])
      println(AvroSchema[GetDetailsResult])
    }

    it("should serialize and deserialize version 1") {
      writeBinary[AppointmentCreatedV1](expectedV1, fileV1)

      assert(read[AppointmentCreatedV1](fileV1, schemaV1) === expectedV1)
    }

    it("should serialize and deserialize version 2") {
      writeBinary[AppointmentCreatedV2](expectedV2, fileV2)

      assert(read[AppointmentCreatedV2](fileV2, schemaV2) === expectedV2)
    }

    it("should serialize and deserialize version 3") {
      writeBinary[AppointmentCreatedV3](expectedV3, fileV3)

      assert(read[AppointmentCreatedV3](fileV3, schemaV3) === expectedV3)
    }

    it("should be backwards compatible") {
      val theFile = new File("appointment-backwards.avro")

      writeBinary[AppointmentCreatedV1](expectedV1, theFile)

      val defaultSubjectFromSchema = "DBZ_KW"
      val defaultDateFromSchema    = ZonedDateTime.parse("2017-12-31T12:34:45Z")
      val expectedWithDefaultsV2 = expectedV1.map(v1 =>
        AppointmentCreatedV2(v1.id, defaultSubjectFromSchema, defaultDateFromSchema))
      val expectedWithDefaultsV3 =
        expectedWithDefaultsV2.map(v2 => AppointmentCreatedV3(v2.id, v2.subject, v2.start))
      // YES !! If you want to have backward compatibility provide both the schema that was used to write
      // AND the schema used to read it now.
      // The 'trick' came from https://stackoverflow.com/questions/34733604/avro-schema-doesnt-honor-backward-compatibilty
      assert(read[AppointmentCreatedV2](theFile, schemaV1, schemaV2) === expectedWithDefaultsV2)
      assert(read[AppointmentCreatedV3](theFile, schemaV1, schemaV3) === expectedWithDefaultsV3)
    }

    it("should be forwards compatible") {
      val file = new File("appointment-forward.avro")
      writeBinary[AppointmentCreatedV3](expectedV3, file)

      val expectedV2 = expectedV3.map(
        v3 =>
          AppointmentCreatedV2(v3.id,
                               v3.subject,
                               v3.start,
                               v3.branchOffice.map(o => BranchOfficeV1(o.branchId))))
      val expectedV1 = expectedV2.map(v2 => AppointmentCreatedV1(v2.id))

      read[AppointmentCreatedV1](file, schemaV3, schemaV1) should contain theSameElementsAs expectedV1
      read[AppointmentCreatedV2](file, schemaV3, schemaV2) should contain theSameElementsAs expectedV2
    }

    it("should measure the file size with and without schema") {
      val withoutSchema = new File("without-schema.avro")
      val withSchema    = new File("wit-schema.avro")

      writeBinary[AppointmentCreatedV2](expectedV2 ++ expectedV2, withoutSchema)
      writeData[AppointmentCreatedV2](expectedV2 ++ expectedV2, withSchema)

      println(withoutSchema.length() + " - " + withSchema.length())
    }
  }

  private def read[T: SchemaFor: FromRecord](file: File,
                                             writerSchema: Schema,
                                             readerSchema: Schema): Seq[T] = {
    val is = MyAvroInputStream
      .binary[T](new SeekableFileInput(file), writerSchema, readerSchema)
    readAndReturn(is)
  }

  private def read[T: SchemaFor: FromRecord](file: File, schema: Schema): Seq[T] = {
    val is = AvroInputStream.binary[T](file, schema)
    readAndReturn(is)
  }

  private def read[T: SchemaFor: FromRecord](file: File): Seq[T] = {
    val is = AvroInputStream.binary[T](file)
    readAndReturn(is)
  }

  private def readAndReturn[T: SchemaFor: FromRecord](is: AvroBinaryInputStream[T]) = {
    val appointments = is.iterator().toSeq
    is.close()
    appointments
  }

  private def readSchema(schema: String) = {
    (new Schema.Parser)
      .parse(new FileInputStream("src/main/resources/" + schema))
  }

  def writeBinary[T: SchemaFor: ToRecord](ts: Seq[T], file: File): Unit = {
    val os = AvroOutputStream.binary[T](file)
    ts.foreach(os.write)
    os.close()
  }

  def writeData[T: SchemaFor: ToRecord](ts: Seq[T], file: File): Unit = {
    val os = AvroOutputStream.data[T](file)
    ts.foreach(os.write)
    os.close()
  }

}
