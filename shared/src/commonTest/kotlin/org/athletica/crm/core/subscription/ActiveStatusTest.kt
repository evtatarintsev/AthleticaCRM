package org.athletica.crm.core.subscription

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Юнит-тесты выводимых на дату свойств активного абонемента. */
class ActiveStatusTest {
    private fun date(day: Int, month: Int, year: Int = 2026) = LocalDate(year, month, day)

    private fun status(
        endDate: LocalDate,
        remaining: Int?,
        today: LocalDate,
        startDate: LocalDate = date(1, 10),
    ) = ActiveStatus.of(startDate, endDate, remaining, today)

    @Test
    fun `последний день срока — в сроке и заканчивается по дате`() {
        val s = status(date(16, 10, 2026), 5, date(16, 10, 2026))
        assertEquals(TermPosition.IN_TERM, s.termPosition)
        assertEquals(setOf(EndingSoonReason.BY_DATE), s.endingSoon)
        assertFalse(s.closePending)
    }

    @Test
    fun `ровно 7 дней — заканчивается по дате, но не по занятиям`() {
        assertEquals(setOf(EndingSoonReason.BY_DATE), status(date(23, 10, 2026), 5, date(16, 10, 2026)).endingSoon)
    }

    @Test
    fun `8 дней — не заканчивается`() {
        assertEquals(emptySet(), status(date(24, 10, 2026), 5, date(16, 10, 2026)).endingSoon)
    }

    @Test
    fun `одно занятие — заканчивается по занятиям`() {
        assertEquals(setOf(EndingSoonReason.BY_SESSIONS), status(date(16, 11, 2026), 1, date(16, 10, 2026)).endingSoon)
    }

    @Test
    fun `ноль занятий — исчерпан, к закрытию, не заканчивается`() {
        val s = status(date(16, 11, 2026), 0, date(16, 10, 2026))
        assertEquals(Remaining.Exhausted, s.remaining)
        assertTrue(s.closePending)
        assertEquals(emptySet(), s.endingSoon)
    }

    @Test
    fun `безлимит не заканчивается по занятиям`() {
        val s = status(date(16, 11, 2026), null, date(16, 10, 2026))
        assertEquals(Remaining.Unlimited, s.remaining)
        assertEquals(emptySet(), s.endingSoon)
        assertFalse(s.closePending)
    }

    @Test
    fun `будущая дата начала — не начался, без признаков`() {
        val s = status(date(16, 11, 2026), 1, date(16, 10, 2026), startDate = date(17, 10, 2026))
        assertEquals(TermPosition.NOT_STARTED, s.termPosition)
        assertEquals(emptySet(), s.endingSoon)
        assertFalse(s.closePending)
    }

    @Test
    fun `срок вышел — к закрытию`() {
        val s = status(date(15, 10, 2026), 3, date(16, 10, 2026))
        assertEquals(TermPosition.EXPIRED, s.termPosition)
        assertTrue(s.closePending)
        assertEquals(emptySet(), s.endingSoon)
    }
}
