package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Конкретная реализация [Session] на основе данных из PostgreSQL. */
class DbSession(
    override val id: SessionId,
    override val groupId: GroupId,
    override val groupName: String,
    override val date: LocalDate,
    override val startTime: LocalTime,
    override val endTime: LocalTime,
    override val status: String,
    override val isManual: Boolean,
    override val isRescheduled: Boolean,
    override val originDayOfWeek: String?,
    override val originStartTime: LocalTime?,
    override val originDate: LocalDate?,
    override val notes: String?,
) : Session {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun cancel() {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_CANCEL", "Можно отменить только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions SET status = 'cancelled', cancelled_at = now()
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
    ) {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_RESCHEDULE", "Можно перенести только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions
                SET date = :date, start_time = :startTime, end_time = :endTime, is_rescheduled = true
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("date", newDate.toJavaLocalDate())
            .bind("startTime", newStartTime.toString())
            .bind("endTime", newEndTime.toString())
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun complete() {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_COMPLETE", "Можно завершить только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions SET status = 'completed', completed_at = now()
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }
}
