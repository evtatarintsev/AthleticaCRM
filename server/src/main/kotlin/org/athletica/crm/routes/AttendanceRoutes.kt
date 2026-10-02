package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.athletica.crm.api.schemas.attendance.AttendanceLabelListRequest
import org.athletica.crm.api.schemas.attendance.AttendanceLabelListResponse
import org.athletica.crm.api.schemas.attendance.AttendanceLabelRequest
import org.athletica.crm.api.schemas.attendance.AttendanceLabelSchema
import org.athletica.crm.api.schemas.attendance.ClientAttendanceRequest
import org.athletica.crm.api.schemas.attendance.ClientAttendanceResponse
import org.athletica.crm.api.schemas.attendance.CompleteSessionRequest
import org.athletica.crm.api.schemas.attendance.CreateAttendanceLabelRequest
import org.athletica.crm.api.schemas.attendance.GroupAttendanceRequest
import org.athletica.crm.api.schemas.attendance.GroupAttendanceResponse
import org.athletica.crm.api.schemas.attendance.JournalParticipantRequest
import org.athletica.crm.api.schemas.attendance.MarkAttendanceRequest
import org.athletica.crm.api.schemas.attendance.MarkRemainingAbsentRequest
import org.athletica.crm.api.schemas.attendance.SessionJournalRequest
import org.athletica.crm.api.schemas.attendance.SessionJournalResponse
import org.athletica.crm.api.schemas.attendance.UpdateAttendanceLabelRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.attendance.AttendanceLabel
import org.athletica.crm.domain.attendance.AttendanceLabels
import org.athletica.crm.domain.attendance.AttendanceMark
import org.athletica.crm.domain.attendance.SessionJournals
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.attendance.ClientAttendanceQuery
import org.athletica.crm.read.attendance.GroupAttendanceQuery
import org.athletica.crm.storage.Database

/** Предельная длина периода отчёта о посещаемости в днях, включая обе границы. */
internal const val MAX_ATTENDANCE_PERIOD_DAYS = 366

/**
 * Регистрирует маршруты журнала посещаемости: справочник меток, журнал занятия,
 * проведение занятия и сводки посещаемости. Команды над журналом отвечают обновлённым
 * журналом, чтобы карточка занятия не делала повторный запрос.
 */
context(db: Database)
fun RouteWithContext.attendanceRoutes(
    labels: AttendanceLabels,
    journals: SessionJournals,
    sessions: Sessions,
    views: ReadViews,
) {
    route("/attendance-labels") {
        get<AttendanceLabelListRequest, AttendanceLabelListResponse>("/list") { request ->
            db.transaction {
                AttendanceLabelListResponse(labels.list(request.includeArchived).map { it.toSchema() })
            }
        }

        post<CreateAttendanceLabelRequest, Unit>("/create") { request ->
            db.transaction {
                labels.new(request.id, request.name, request.scope).save()
            }
        }

        post<UpdateAttendanceLabelRequest, Unit>("/update") { request ->
            db.transaction {
                labels.byId(request.id).renamed(request.name).movedTo(request.position).save()
            }
        }

        post<AttendanceLabelRequest, Unit>("/archive") { request ->
            db.transaction {
                labels.byId(request.id).archived().save()
            }
        }

        post<AttendanceLabelRequest, Unit>("/restore") { request ->
            db.transaction {
                labels.byId(request.id).restored().save()
            }
        }
    }

    route("/sessions") {
        get<SessionJournalRequest, SessionJournalResponse>("/journal") { request ->
            db.transaction {
                views.sessionJournal.journal(request.sessionId)
            }
        }

        post<MarkAttendanceRequest, SessionJournalResponse>("/journal/mark") { request ->
            val mark = AttendanceMark.from(request.presence, request.labelIds).fold({ raise(it) }, { it })
            db.transaction {
                journals.byId(request.sessionId).mark(request.clientId, mark)
                views.sessionJournal.journal(request.sessionId)
            }
        }

        post<MarkRemainingAbsentRequest, SessionJournalResponse>("/journal/mark-remaining-absent") { request ->
            db.transaction {
                journals.byId(request.sessionId).markRemainingAbsent(request.labelId)
                views.sessionJournal.journal(request.sessionId)
            }
        }

        post<JournalParticipantRequest, SessionJournalResponse>("/journal/add-participant") { request ->
            db.transaction {
                journals.byId(request.sessionId).addOneTime(request.clientId)
                views.sessionJournal.journal(request.sessionId)
            }
        }

        post<JournalParticipantRequest, SessionJournalResponse>("/journal/remove-participant") { request ->
            db.transaction {
                journals.byId(request.sessionId).removeOneTime(request.clientId)
                views.sessionJournal.journal(request.sessionId)
            }
        }

        post<CompleteSessionRequest, SessionJournalResponse>("/complete") { request ->
            db.transaction {
                sessions.byId(request.sessionId).complete()
                views.sessionJournal.journal(request.sessionId)
            }
        }
    }

    route("/attendance") {
        get<GroupAttendanceRequest, GroupAttendanceResponse>("/group") { request ->
            ensureAttendancePeriod(request.from, request.to)
            db.transaction {
                views.groupAttendance.summary(GroupAttendanceQuery(request.groupId, request.from, request.to))
            }
        }

        get<ClientAttendanceRequest, ClientAttendanceResponse>("/client") { request ->
            ensureAttendancePeriod(request.from, request.to)
            db.transaction {
                views.clientAttendance.list(ClientAttendanceQuery(request.clientId, request.from, request.to))
            }
        }
    }
}

/** Схема метки для ответа. */
private fun AttendanceLabel.toSchema() = AttendanceLabelSchema(id = id, name = name, scope = scope, position = position, isArchived = isArchived)

/** Проверяет период отчёта [from]..[to]: конец не раньше начала и не длиннее [MAX_ATTENDANCE_PERIOD_DAYS]. */
context(ctx: RequestContext, raise: Raise<DomainError>)
private fun ensureAttendancePeriod(from: LocalDate, to: LocalDate) {
    if (to < from) {
        raise(CommonDomainError("INVALID_ATTENDANCE_PERIOD", Messages.InvalidAttendancePeriod.localize()))
    }
    if (from.daysUntil(to) + 1 > MAX_ATTENDANCE_PERIOD_DAYS) {
        raise(CommonDomainError("ATTENDANCE_PERIOD_TOO_LONG", Messages.AttendancePeriodTooLong.localize()))
    }
}
