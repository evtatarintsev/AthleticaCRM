package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.storage.Transaction

@Serializable
private data class NewSessionSnapshot(
    val id: SessionId,
    val groupId: GroupId,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isManual: Boolean,
)

/**
 * Декоратор [Sessions], добавляющий запись в журнал аудита при создании занятий.
 * Генерированные занятия не логируются (слишком много событий), только ручные.
 */
class AuditSessions(private val delegate: Sessions, private val audit: AuditLog) : Sessions by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: SessionId,
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        notes: String?,
        originDayOfWeek: String?,
        originStartTime: LocalTime?,
        originDate: LocalDate?,
    ): Session? {
        val session = delegate.new(id, groupId, date, startTime, endTime, notes, originDayOfWeek, originStartTime, originDate)
        if (session != null && originDayOfWeek == null) {
            audit.logCreate(
                "session",
                id,
                Json.encodeToString(NewSessionSnapshot(id, groupId, date, startTime, endTime, isManual = true)),
            )
        }
        return session?.let { AuditSession(it, audit) }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: SessionId): Session = AuditSession(delegate.byId(id), audit)
}
