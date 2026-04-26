package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Одно занятие группы — конкретный экземпляр расписания. */
interface Session {
    val id: SessionId

    /** Группа, которой принадлежит занятие. */
    val groupId: GroupId

    /** Название группы (денормализовано для удобства отображения). */
    val groupName: String

    /** Дата проведения занятия. */
    val date: LocalDate

    /** Время начала занятия. */
    val startTime: LocalTime

    /** Время окончания занятия. */
    val endTime: LocalTime

    /**
     * Статус занятия: `scheduled`, `completed`, `cancelled`.
     * Соответствует enum-типу `session_status` в БД.
     */
    val status: String

    /** Занятие создано вручную (не из расписания). */
    val isManual: Boolean

    /** Занятие было перенесено с оригинальной даты/времени. */
    val isRescheduled: Boolean

    /** День недели оригинального слота расписания (например, `MONDAY`). Null для ручных занятий. */
    val originDayOfWeek: String?

    /** Время начала оригинального слота. Null для ручных занятий. */
    val originStartTime: LocalTime?

    /** Дата оригинального слота (неизменна при переносе). Null для ручных занятий. */
    val originDate: LocalDate?

    /** Произвольные заметки к занятию. */
    val notes: String?

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun cancel()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
    )

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun complete()
}
