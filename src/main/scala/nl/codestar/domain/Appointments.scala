package nl.codestar.domain

import akka.actor.{Actor, ActorLogging, Props}

// this would be the implementation in a non-clustered world.

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
