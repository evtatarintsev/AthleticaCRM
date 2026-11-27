package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.reports.AmountReportValue
import org.athletica.crm.api.schemas.reports.CountReportValue
import org.athletica.crm.api.schemas.reports.PercentReportValue
import org.athletica.crm.api.schemas.reports.ReportFilter
import org.athletica.crm.api.schemas.reports.ReportValue
import org.athletica.crm.api.schemas.reports.ReportsTableExportRequest
import org.athletica.crm.api.schemas.reports.ReportsTableResponse
import org.athletica.crm.api.schemas.reports.reportChange
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.reports.ReportPeriods
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.reports.ReportTableSource
import org.athletica.crm.routes.export.ExportCell
import org.athletica.crm.routes.export.ExportTable
import org.athletica.crm.routes.export.exportFile
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction

/**
 * Раздел «Отчёты»: таблица показателей и её выгрузка. «Сегодня» — из [calendar] в часовом
 * поясе организации, строки — из источников [ReadViews.reportTableSources], на которые у
 * сотрудника хватает прав.
 */
context(db: Database)
fun RouteWithContext.reportsRoutes(
    calendar: OrgCalendar,
    views: ReadViews,
) {
    route("/reports") {
        post<ReportFilter, ReportsTableResponse>("/table") { r ->
            db.transaction { reportsTable(r, calendar.today(), views.reportTableSources) }
        }
        post<ReportsTableExportRequest, ByteArray>("/table/export") { r, call ->
            val table = db.transaction { reportsTable(r.filter, calendar.today(), views.reportTableSources) }
            call.exportFile("reports-${table.period.from}-${table.period.to}", r.format, table.toExport())
        }
    }
}

/**
 * Таблица показателей по фильтру [filter] при сегодняшней дате [today]: требует
 * `CAN_VIEW_REPORTS`, филиал — из доступных сотруднику, строки — только из тех [sources],
 * на все права которых хватает прав сотрудника.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun reportsTable(
    filter: ReportFilter,
    today: LocalDate,
    sources: List<ReportTableSource>,
): ReportsTableResponse {
    ensure(ctx.hasPermission(UserPermission.CAN_VIEW_REPORTS)) { CommonDomainError("PERMISSION_DENIED", ReportsAccessDenied.localize()) }
    filter.branchId?.let { ensure(ctx.employee.availableBranches.contains(it)) { CommonDomainError("PERMISSION_DENIED", ReportsAccessDenied.localize()) } }
    val periods = ReportPeriods.of(filter, today)
    val rows = sources.filter { source -> source.requires.all { ctx.hasPermission(it) } }.flatMap { it.rows(filter, periods) }
    return ReportsTableResponse(periods.period, periods.basePeriod, rows)
}

/** Таблица выгрузки: «Показатель», «Период», «Прошлый период», «Изменение» на языке сотрудника. */
context(ctx: EmployeeRequestContext)
private fun ReportsTableResponse.toExport(): ExportTable =
    ExportTable(
        headers = listOf(ReportsColumn.Metric, ReportsColumn.Period, ReportsColumn.BasePeriod, ReportsColumn.Change).map { it.localize() },
        rows =
            rows.map { row ->
                listOf(
                    ExportCell.Text(row.label),
                    row.value.cell(),
                    row.base?.cell() ?: ExportCell.Empty,
                    reportChange(row.value, row.base)?.let(ExportCell::Percent) ?: ExportCell.Empty,
                )
            },
    )

/** Ячейка выгрузки значения показателя. */
private fun ReportValue.cell(): ExportCell =
    when (this) {
        is AmountReportValue -> ExportCell.Amount(amount)
        is CountReportValue -> ExportCell.Number(count)
        is PercentReportValue -> ExportCell.Percent(share)
    }

private object ReportsAccessDenied : LocalizationKey {
    override val ru = "Недостаточно прав для просмотра отчётов"
    override val en = "You do not have permission to view reports"
}

/** Заголовки колонок выгрузки таблицы показателей. */
private enum class ReportsColumn(
    override val ru: String,
    override val en: String,
) : LocalizationKey {
    Metric("Показатель", "Metric"),
    Period("Период", "Period"),
    BasePeriod("Прошлый период", "Previous period"),
    Change("Изменение", "Change"),
}
