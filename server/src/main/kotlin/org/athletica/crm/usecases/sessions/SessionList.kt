package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.sessions.SessionListItem
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Session
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database

/**
 * Возвращает список занятий за период, предварительно генерируя недостающие.
 * Если [groupId] не указан — возвращает занятия всех групп организации.
 */
context(ctx: RequestContext, raise: Raise<DomainError>)
suspend fun sessionList(
    db: Database,
    groups: Groups,
    sessions: Sessions,
    groupId: GroupId?,
    from: LocalDate,
    to: LocalDate,
): SessionListResponse {
    db.transaction {
        if (groupId != null) {
            generateSessions(groups, sessions, groupId, from, to)
        } else {
            groups.list().forEach { group ->
                generateSessions(groups, sessions, group.id, from, to)
            }
        }
    }
    return db.transaction {
        val list =
            if (groupId != null) {
                sessions.list(groupId, from, to)
            } else {
                sessions.listAll(from, to)
            }
        val groupNames =
            if (groupId != null) {
                mapOf(groupId to groups.byId(groupId).name)
            } else {
                groups.list().associate { it.id to it.name }
            }
        SessionListResponse(list.map { it.toListItem(groupNames.getValue(it.groupId)) })
    }
}

fun Session.toListItem(groupName: String) =
    SessionListItem(
        id = id,
        groupId = groupId,
        groupName = groupName,
        date = date,
        startTime = startTime,
        endTime = endTime,
        hallId = hallId,
        status = status,
        isManual = isManual,
        isRescheduled = isRescheduled,
        notes = notes,
        employeeIds = employeeIds,
        isEmployeeAssignmentOverridden = isEmployeeAssignmentOverridden,
    )
