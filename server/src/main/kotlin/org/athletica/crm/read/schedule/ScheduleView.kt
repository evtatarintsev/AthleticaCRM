package org.athletica.crm.read.schedule

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция страницы «Расписание»: занятия текущего филиала за период
 * с названиями группы, зала, тренеров и дисциплин и ключом палитры карточки.
 * Только читает уже материализованные занятия.
 */
interface ScheduleView {
    /** Возвращает занятия периода и фильтров из [query], отсортированные по дате и времени начала. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: ScheduleQuery): ScheduleListResponse
}

/** Параметры выборки расписания; нормализованы маршрутом. */
data class ScheduleQuery(
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
    /** Залы занятия; `null` — фильтр не задан. */
    val hallIds: List<HallId>? = null,
    /** Дисциплины группы занятия; `null` — фильтр не задан. */
    val disciplineIds: List<DisciplineId>? = null,
    /** Тренеры занятия; `null` — фильтр не задан. */
    val employeeIds: List<EmployeeId>? = null,
)
