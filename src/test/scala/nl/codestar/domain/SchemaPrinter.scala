package nl.codestar.domain

import com.sksamuel.avro4s.AvroSchema

object SchemaPrinter extends App {
  val events = List(AppointmentCreatedV1, AppointmentCreatedV2)
  val commands = List(CreateAppointmentV1, CreateAppointmentV2)

  println(AppointmentCreatedV1 + ": " + AvroSchema[AppointmentCreatedV1])
  println(AppointmentCreatedV2 + ": " + AvroSchema[AppointmentCreatedV2])
}
