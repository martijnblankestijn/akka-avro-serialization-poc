package nl.codestar.persistence

import akka.persistence.journal._
import nl.codestar.domain.{
  AppointmentCreatedV1,
  AppointmentCreatedV2,
  AppointmentCreatedV3,
  AppointmentEvent
}
import org.slf4j.LoggerFactory.getLogger
import DomainObjectEventAdapter._
import AppointmentEventEnricher._

class DomainObjectEventAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = ""
  override def toJournal(event: Any): Any = {
    if (logger.isDebugEnabled())
      logger.debug("Tagging event {} with {}", eventToString(event), tags, "")

    event match {
      case _: AppointmentEvent => Tagged(event, tags)
      case _                   => event
    }
  }

  private def eventToString(event: Any): String = event.toString.replace('\n', ' ')
}

object DomainObjectEventAdapter {
  private val logger = getLogger(classOf[DomainObjectEventAdapter])
  private val tags   = Set("appointment")
}

class AppointmentEventEnricher extends ReadEventAdapter {
  override def fromJournal(event: Any, manifest: String): EventSeq = {
    logger.debug(event.toString)
    EventSeq.single(event)
  }
}
object AppointmentEventEnricher {
  private val logger = getLogger(classOf[AppointmentEventEnricher])
}
