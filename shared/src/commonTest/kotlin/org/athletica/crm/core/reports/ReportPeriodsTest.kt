package org.athletica.crm.core.reports

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.reports.ReportCompareMode
import org.athletica.crm.api.schemas.reports.ReportFilter
import org.athletica.crm.api.schemas.reports.ReportPeriodKind
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.errors.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Правило периода и прошлого периода отчётов (design §3 изменения `reports-page`). */
class ReportPeriodsTest {
    private fun d(
        year: Int,
        month: Int,
        day: Int,
    ) = LocalDate(year, month, day)

    private fun range(
        from: LocalDate,
        to: LocalDate,
    ) = DateRange(from, to)

    private fun periods(
        filter: ReportFilter,
        today: LocalDate,
    ): ReportPeriods = assertIs<Either.Right<ReportPeriods>>(either { ReportPeriods.of(filter, today) }).value

    private fun error(
        filter: ReportFilter,
        today: LocalDate,
    ): String = assertIs<Either.Left<AppError>>(either { ReportPeriods.of(filter, today) }).value.code

    @Test
    fun `этот месяц — с 1-го по сегодня против того же отрезка прошлого месяца`() {
        val result = periods(ReportFilter(ReportPeriodKind.MONTH), d(2026, 11, 27))
        assertEquals(range(d(2026, 11, 1), d(2026, 11, 27)), result.period)
        assertEquals(range(d(2026, 10, 1), d(2026, 10, 27)), result.basePeriod)
    }

    @Test
    fun `этот месяц 31 марта сравнивается с февралём до его последнего дня`() {
        assertEquals(range(d(2026, 2, 1), d(2026, 2, 28)), periods(ReportFilter(ReportPeriodKind.MONTH), d(2026, 3, 31)).basePeriod)
        assertEquals(range(d(2028, 2, 1), d(2028, 2, 29)), periods(ReportFilter(ReportPeriodKind.MONTH), d(2028, 3, 31)).basePeriod)
    }

    @Test
    fun `этот месяц в январе сравнивается с декабрём прошлого года`() {
        assertEquals(range(d(2025, 12, 1), d(2025, 12, 15)), periods(ReportFilter(ReportPeriodKind.MONTH), d(2026, 1, 15)).basePeriod)
    }

    @Test
    fun `прошлый месяц — весь месяц против позапрошлого, через год`() {
        val result = periods(ReportFilter(ReportPeriodKind.LAST_MONTH), d(2026, 1, 10))
        assertEquals(range(d(2025, 12, 1), d(2025, 12, 31)), result.period)
        assertEquals(range(d(2025, 11, 1), d(2025, 11, 30)), result.basePeriod)
    }

    @Test
    fun `30 дней — сегодня и 29 дней до против 30 дней вплотную перед ними`() {
        val result = periods(ReportFilter(ReportPeriodKind.LAST_30_DAYS), d(2026, 3, 1))
        assertEquals(range(d(2026, 1, 31), d(2026, 3, 1)), result.period)
        assertEquals(range(d(2026, 1, 1), d(2026, 1, 30)), result.basePeriod)
    }

    @Test
    fun `квартал — с начала квартала против того же отрезка прошлого`() {
        val result = periods(ReportFilter(ReportPeriodKind.QUARTER), d(2026, 5, 31))
        assertEquals(range(d(2026, 4, 1), d(2026, 5, 31)), result.period)
        assertEquals(range(d(2026, 1, 1), d(2026, 2, 28)), result.basePeriod)
    }

    @Test
    fun `первый квартал сравнивается с последним кварталом прошлого года`() {
        val result = periods(ReportFilter(ReportPeriodKind.QUARTER), d(2026, 2, 10))
        assertEquals(range(d(2026, 1, 1), d(2026, 2, 10)), result.period)
        assertEquals(range(d(2025, 10, 1), d(2025, 11, 10)), result.basePeriod)
    }

    @Test
    fun `свой период — те же дни перед ним`() {
        val filter = ReportFilter(ReportPeriodKind.CUSTOM, d(2026, 3, 10), d(2026, 3, 19), ReportCompareMode.DAYS_BEFORE)
        assertEquals(range(d(2026, 2, 28), d(2026, 3, 9)), periods(filter, d(2026, 11, 27)).basePeriod)
    }

    @Test
    fun `свой период — тот же отрезок месяцем раньше с обрезкой до конца месяца`() {
        val filter = ReportFilter(ReportPeriodKind.CUSTOM, d(2026, 3, 15), d(2026, 3, 31), ReportCompareMode.MONTH_BEFORE)
        val result = periods(filter, d(2026, 11, 27))
        assertEquals(range(d(2026, 3, 15), d(2026, 3, 31)), result.period)
        assertEquals(range(d(2026, 2, 15), d(2026, 2, 28)), result.basePeriod)
    }

    @Test
    fun `свой период проверяется при создании`() {
        val today = d(2026, 11, 27)
        listOf(
            ReportFilter(ReportPeriodKind.CUSTOM, null, today),
            ReportFilter(ReportPeriodKind.CUSTOM, today, null),
            ReportFilter(ReportPeriodKind.CUSTOM, d(2026, 11, 20), d(2026, 11, 10)),
            ReportFilter(ReportPeriodKind.CUSTOM, d(2026, 11, 20), d(2026, 11, 28)),
            ReportFilter(ReportPeriodKind.CUSTOM, d(2025, 11, 26), today),
        ).forEach { assertEquals("REPORT_PERIOD_INVALID", error(it, today), it.toString()) }
        assertEquals(range(d(2025, 11, 27), today), periods(ReportFilter(ReportPeriodKind.CUSTOM, d(2025, 11, 27), today), today).period)
    }

    @Test
    fun `фильтр читается из JSON по именам пунктов адреса`() {
        val filter = Json.decodeFromString<ReportFilter>("""{"period":"custom","from":"2026-11-01","to":"2026-11-05","compare":"month-before"}""")
        assertEquals(ReportFilter(ReportPeriodKind.CUSTOM, d(2026, 11, 1), d(2026, 11, 5), ReportCompareMode.MONTH_BEFORE), filter)
        assertEquals(ReportFilter(), Json.decodeFromString<ReportFilter>("{}"))
    }
}
