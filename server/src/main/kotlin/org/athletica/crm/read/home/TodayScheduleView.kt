package org.athletica.crm.read.home

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция расписания на день для главной страницы: запланированные занятия
 * с названиями группы и зала, отсортированные по времени начала.
 */
interface TodayScheduleView {
    /** Возвращает запланированные занятия за [date]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun sessionsOn(date: LocalDate): TodaySessionsResponse
}
