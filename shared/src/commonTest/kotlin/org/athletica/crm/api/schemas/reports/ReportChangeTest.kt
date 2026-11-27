package org.athletica.crm.api.schemas.reports

import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Правило изменения показателя к прошлому периоду [reportChange]. */
class ReportChangeTest {
    private fun rub(minor: Long) = AmountReportValue(Money(minor, Currency.RUB))

    @Test
    fun `рост суммы — относительное изменение`() {
        assertEquals(0.5, reportChange(rub(150), rub(100)))
    }

    @Test
    fun `рост от отрицательной суммы остаётся ростом`() {
        assertEquals(1.5, reportChange(rub(50), rub(-100)))
        assertEquals(-1.0, reportChange(rub(-200), rub(-100)))
    }

    @Test
    fun `количество и доли`() {
        assertEquals(-0.25, reportChange(CountReportValue(3), CountReportValue(4)))
        assertEquals(0.25, reportChange(PercentReportValue(0.75), PercentReportValue(0.5)))
    }

    @Test
    fun `сравнивать не с чем`() {
        assertNull(reportChange(rub(100), null))
        assertNull(reportChange(rub(100), rub(0)))
        assertNull(reportChange(CountReportValue(1), rub(100)))
    }
}
