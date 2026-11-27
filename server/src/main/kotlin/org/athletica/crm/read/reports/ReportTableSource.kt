package org.athletica.crm.read.reports

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.reports.ReportFilter
import org.athletica.crm.api.schemas.reports.ReportsTableRow
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.reports.ReportPeriods
import org.athletica.crm.storage.Transaction

/**
 * Источник строк таблицы показателей отчётов. Каждое изменение отчёта добавляет свой источник
 * поверх своей проекции-сводки и регистрирует его в [org.athletica.crm.read.ReadViews.reportTableSources].
 */
interface ReportTableSource {
    /** Права сверх `CAN_VIEW_REPORTS`, без которых строки источника не попадают ни в ответ, ни в файл. */
    val requires: Set<UserPermission>

    /** Строки показателей по фильтру [filter] за периоды [periods]; подписи — на языке сотрудника. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun rows(
        filter: ReportFilter,
        periods: ReportPeriods,
    ): List<ReportsTableRow>
}
