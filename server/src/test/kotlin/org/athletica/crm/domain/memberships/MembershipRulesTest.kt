package org.athletica.crm.domain.memberships

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.core.subscription.TermPosition
import org.athletica.crm.employeeContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Юнит-тесты чистых правил абонемента: срок, выводимые свойства, правила выдачи. */
class MembershipRulesTest {
    private val ctx = employeeContext(orgId = OrgId.new())

    private fun months(n: Int) = MembershipDuration(n, DurationUnit.MONTHS)

    private fun date(day: Int, month: Int, year: Int = 2027) = LocalDate(year, month, day)

    @Test
    fun `срок 1 день заканчивается в день начала`() {
        assertEquals(date(16, 10, 2026), MembershipDuration(1, DurationUnit.DAYS).endDateFrom(date(16, 10, 2026)))
    }

    @Test
    fun `срок в месяцах включает дату начала и дату окончания`() {
        assertEquals(date(15, 11, 2026), months(1).endDateFrom(date(16, 10, 2026)))
        assertEquals(date(28, 2), months(1).endDateFrom(date(1, 2)))
        assertEquals(date(31, 3), months(1).endDateFrom(date(1, 3)))
        assertEquals(date(31, 5), months(1).endDateFrom(date(1, 5)))
        assertEquals(date(30, 4), months(1).endDateFrom(date(31, 3)))
        assertEquals(date(28, 2), months(1).endDateFrom(date(31, 1)))
        assertEquals(date(29, 2, 2028), months(1).endDateFrom(date(31, 1, 2028)))
        assertEquals(date(28, 2, 2029), months(12).endDateFrom(date(29, 2, 2028)))
    }

    private fun status(
        endDate: LocalDate,
        remaining: Int?,
        today: LocalDate,
        startDate: LocalDate = date(1, 10, 2026),
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

    @Test
    fun `уведомление к закрытию зависит от режима закрытия`() {
        val expired = status(date(15, 10, 2026), 3, date(16, 10, 2026))
        val exhausted = status(date(16, 11, 2026), 0, date(16, 10, 2026))
        assertTrue(expired.needsCloseNotification(CloseMode.MANUAL))
        assertTrue(exhausted.needsCloseNotification(CloseMode.MANUAL))
        assertTrue(expired.needsCloseNotification(CloseMode.ON_LAST_SESSION))
        assertFalse(exhausted.needsCloseNotification(CloseMode.ON_EXPIRY))
        assertFalse(expired.needsCloseNotification(CloseMode.ON_EXPIRY))
    }

    private fun outcome(activation: Activation, closeMode: CloseMode = CloseMode.MANUAL, sessions: Int? = 8): Either<DomainError, IssueOutcome> = either { context(ctx, this) { issueOutcome(activation, closeMode, sessions, listOf(GroupId.new()), date(16, 10, 2026)) } }

    @Test
    fun `сразу активирует датой выдачи`() {
        assertEquals(IssueOutcome.Active(date(16, 10, 2026)), assertIs<Either.Right<IssueOutcome>>(outcome(Activation.Immediate)).value)
    }

    @Test
    fun `с прошедшей даты активирует этой датой`() {
        val result = outcome(Activation.OnDate(date(10, 10, 2026)))
        assertEquals(IssueOutcome.Active(date(10, 10, 2026)), assertIs<Either.Right<IssueOutcome>>(result).value)
    }

    @Test
    fun `с сегодняшней даты активирует сразу`() {
        val result = outcome(Activation.OnDate(date(16, 10, 2026)))
        assertEquals(IssueOutcome.Active(date(16, 10, 2026)), assertIs<Either.Right<IssueOutcome>>(result).value)
    }

    @Test
    fun `с будущей даты — неактивирован`() {
        assertEquals(IssueOutcome.Inactive, assertIs<Either.Right<IssueOutcome>>(outcome(Activation.OnDate(date(20, 10, 2026)))).value)
    }

    @Test
    fun `крайняя дата раньше выдачи отклоняется`() {
        val error = assertIs<Either.Left<DomainError>>(outcome(Activation.OnFirstVisit(date(15, 10, 2026)))).value
        assertEquals("ACTIVATION_DEADLINE_BEFORE_ISSUE", error.code)
    }

    @Test
    fun `крайняя дата в день выдачи допустима`() {
        assertEquals(IssueOutcome.Inactive, assertIs<Either.Right<IssueOutcome>>(outcome(Activation.OnFirstVisit(date(16, 10, 2026)))).value)
    }

    @Test
    fun `безлимит с закрытием по занятиям отклоняется`() {
        val error = assertIs<Either.Left<DomainError>>(outcome(Activation.Immediate, CloseMode.ON_LAST_SESSION, sessions = null)).value
        assertEquals("UNLIMITED_CLOSE_ON_LAST_SESSION", error.code)
    }
}
