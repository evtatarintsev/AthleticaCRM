package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Репозиторий занятий организации. */
interface Sessions {
    /**
     * Создаёт новое занятие.
     * Если занятие с такими [originDayOfWeek]+[originStartTime]+[originDate] уже существует — молча игнорирует
     * (идемпотентная генерация через UNIQUE constraint + ON CONFLICT DO NOTHING).
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: SessionId,
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        notes: String?,
        originDayOfWeek: String?,
        originStartTime: LocalTime?,
        originDate: LocalDate?,
    ): Session?

    /** Возвращает список занятий группы за период [from]..[to]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): List<Session>

    /** Возвращает список всех занятий организации за период [from]..[to]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun listAll(
        from: LocalDate,
        to: LocalDate,
    ): List<Session>

    /** Возвращает занятие по идентификатору. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: SessionId): Session

    /**
     * Возвращает будущие запланированные занятия группы, сгенерированные из указанного слота расписания.
     * Используется при изменении расписания для автоматической отмены занятий из удалённых слотов.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun futureScheduledBySlot(
        groupId: GroupId,
        dayOfWeek: String,
        startTime: LocalTime,
        from: LocalDate,
    ): List<Session>
}
