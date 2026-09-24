package org.athletica.crm.components.groups

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты переходов состояния диалога установки расписания группы. */
class SetScheduleStateTest {
    private val today = LocalDate(2026, 9, 24)

    @Test
    fun `по умолчанию дата вступления в силу — сегодня и в запрос не передаётся`() {
        val state = SetScheduleState(today = today)

        assertEquals(today, state.effectiveFrom)
        assertNull(state.requestedEffectiveFrom)
    }

    @Test
    fun `выбранная будущая дата передаётся в запрос`() {
        val future = LocalDate(2026, 10, 1)
        val state = SetScheduleState(today = today).withEffectiveFrom(future)

        assertEquals(future, state.requestedEffectiveFrom)
    }

    @Test
    fun `дата в прошлом заменяется сегодняшней`() {
        val state = SetScheduleState(today = today).withEffectiveFrom(LocalDate(2026, 9, 1))

        assertEquals(today, state.effectiveFrom)
    }

    @Test
    fun `дата не позже запланированного изменения отменяет его`() {
        val planned = LocalDate(2026, 10, 1)
        val state = SetScheduleState(today = today, plannedChangeAt = planned)

        assertTrue(state.cancelsPlannedChange)
        assertTrue(state.withEffectiveFrom(planned).cancelsPlannedChange)
        assertFalse(state.withEffectiveFrom(LocalDate(2026, 10, 2)).cancelsPlannedChange)
    }
}
