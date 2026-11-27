package org.athletica.crm.core.reports

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.api.schemas.reports.ReportCompareMode
import org.athletica.crm.api.schemas.reports.ReportFilter
import org.athletica.crm.api.schemas.reports.ReportPeriodKind
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError

/**
 * Свой период отчёта: `from` ≤ `to`, не длиннее [MAX_DAYS] дней и не заходит в будущее.
 * Создаётся только через [of].
 */
class CustomPeriod private constructor(
    /** Даты периода включительно. */
    val range: DateRange,
) {
    companion object {
        /** Наибольшая длина своего периода в днях. */
        const val MAX_DAYS = 366

        /** Свой период [from] … [to] при сегодняшней дате [today]. */
        context(raise: Raise<AppError>)
        fun of(
            from: LocalDate?,
            to: LocalDate?,
            today: LocalDate,
        ): CustomPeriod {
            ensureNotNull(from) { invalid("Укажите начало периода") }
            ensureNotNull(to) { invalid("Укажите конец периода") }
            ensure(from <= to) { invalid("Начало периода позже его конца") }
            ensure(to <= today) { invalid("Период не может заходить в будущее") }
            ensure(from.daysUntil(to) < MAX_DAYS) { invalid("Период не может быть длиннее $MAX_DAYS дней") }
            return CustomPeriod(DateRange(from, to))
        }

        /** Ошибка своего периода с сообщением [message]. */
        private fun invalid(message: String) = CommonDomainError("REPORT_PERIOD_INVALID", message)
    }
}

/** Период отчёта [period] и прошлый период [basePeriod], с которым он сравнивается. */
data class ReportPeriods(
    /** Даты периода включительно. */
    val period: DateRange,
    /** Даты прошлого периода включительно. */
    val basePeriod: DateRange,
) {
    companion object {
        /** Периоды по выбору пользователя [filter] при сегодняшней дате [today] организации. */
        context(raise: Raise<AppError>)
        fun of(
            filter: ReportFilter,
            today: LocalDate,
        ): ReportPeriods =
            when (filter.period) {
                ReportPeriodKind.MONTH -> monthsBack(DateRange(today.firstOfMonth(), today), 1)
                ReportPeriodKind.LAST_MONTH -> {
                    val lastMonth = today.firstOfMonth().minus(1, DateTimeUnit.MONTH)
                    ReportPeriods(lastMonth.wholeMonth(), lastMonth.minus(1, DateTimeUnit.MONTH).wholeMonth())
                }
                ReportPeriodKind.LAST_30_DAYS -> daysBefore(DateRange(today.minus(29, DateTimeUnit.DAY), today))
                ReportPeriodKind.QUARTER -> monthsBack(DateRange(today.firstOfQuarter(), today), 3)
                ReportPeriodKind.CUSTOM -> {
                    val range = CustomPeriod.of(filter.from, filter.to, today).range
                    when (filter.compare) {
                        ReportCompareMode.DAYS_BEFORE -> daysBefore(range)
                        ReportCompareMode.MONTH_BEFORE -> monthsBack(range, 1)
                    }
                }
            }

        /** [period] и та же длина вплотную перед ним. */
        private fun daysBefore(period: DateRange): ReportPeriods {
            val length = period.from.daysUntil(period.to) + 1
            return ReportPeriods(period, DateRange(period.from.minus(length, DateTimeUnit.DAY), period.from.minus(1, DateTimeUnit.DAY)))
        }

        /** [period] и тот же отрезок на [months] месяцев раньше; дата за концом месяца — его последний день. */
        private fun monthsBack(
            period: DateRange,
            months: Int,
        ): ReportPeriods = ReportPeriods(period, DateRange(period.from.minus(months, DateTimeUnit.MONTH), period.to.minus(months, DateTimeUnit.MONTH)))

        /** Первое число месяца даты. */
        private fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

        /** Первое число квартала даты. */
        private fun LocalDate.firstOfQuarter(): LocalDate = LocalDate(year, (month.ordinal / 3) * 3 + 1, 1)

        /** Весь месяц, которому принадлежит дата. */
        private fun LocalDate.wholeMonth(): DateRange {
            val first = firstOfMonth()
            return DateRange(first, first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
        }
    }
}
