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

sealed trait AppointmentEvent

// ugly version numbers in case class names
// in 'real life' only the latest would exist

case class BranchOfficeV1(branchId: String)
case class BranchOfficeV2(branchId: String, roomId: Option[UUID] = None)

case class AppointmentCreatedV1(id: UUID) extends AppointmentEvent
case class AppointmentCreatedV2(id: UUID,
                                subject: String,
                                start: ZonedDateTime,
                                branchOffice: Option[BranchOfficeV1] = None)
    extends AppointmentEvent
case class AppointmentCreatedV3(id: UUID,
                                subject: String,
                                start: ZonedDateTime,
                                branchOffice: Option[BranchOfficeV2] = None,
                                tags: Set[String] = Set.empty)
    extends AppointmentEvent
