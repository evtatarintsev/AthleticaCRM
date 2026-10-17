package org.athletica.crm.routes

import org.athletica.crm.api.schemas.attendancecontrol.AttendanceControlSettingsResponse
import org.athletica.crm.api.schemas.attendancecontrol.UpdateAttendanceControlSettingsRequest
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.domain.attendancecontrol.AttendanceControl
import org.athletica.crm.domain.attendancecontrol.AttendanceControls
import org.athletica.crm.domain.attendancecontrol.JournalCloseDelay
import org.athletica.crm.domain.org.Organization
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.orgRoutes(
    organizations: Organizations,
    attendanceControls: AttendanceControls,
) {
    route("/org") {
        get<Unit, OrgSettingsResponse>("/settings") {
            db.transaction {
                organizations
                    .current()
                    .toResponse()
            }
        }

        post<UpdateOrgSettingsRequest, OrgSettingsResponse>("/settings/update") { request ->
            db.transaction {
                organizations
                    .current()
                    .withNew(request.name, request.timezone)
                    .apply { save() }
                    .toResponse()
            }
        }

        get<Unit, AttendanceControlSettingsResponse>("/attendance-control") {
            db.transaction {
                attendanceControls
                    .current()
                    .toResponse()
            }
        }

        post<UpdateAttendanceControlSettingsRequest, AttendanceControlSettingsResponse>("/attendance-control/update") { request ->
            val delay = JournalCloseDelay.from(request.overdueTaskDelayMinutes).bind()
            db.transaction {
                attendanceControls
                    .current()
                    .withNew(request.overdueTaskEnabled, delay)
                    .apply { save() }
                    .toResponse()
            }
        }
    }
}

fun Organization.toResponse() = OrgSettingsResponse(name, timezone, currency)

/** Ответ с настройками контроля посещаемости. */
fun AttendanceControl.toResponse() = AttendanceControlSettingsResponse(overdueTaskEnabled, overdueTaskDelay.minutes)
