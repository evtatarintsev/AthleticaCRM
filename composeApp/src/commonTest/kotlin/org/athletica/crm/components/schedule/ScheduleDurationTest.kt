package org.athletica.crm.components.schedule

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты продолжительности занятия: нулевая часть опускается. */
class ScheduleDurationTest {
    @Test
    fun `двадцать минут — только минуты`() {
        assertEquals(ScheduleDuration.Minutes(20), ScheduleDuration.between(LocalTime(9, 0), LocalTime(9, 20)))
    }

    @Test
    fun `один час — только часы`() {
        assertEquals(ScheduleDuration.Hours(1), ScheduleDuration.between(LocalTime(9, 0), LocalTime(10, 0)))
    }

    @Test
    fun `полтора часа — часы и минуты`() {
        assertEquals(ScheduleDuration.HoursMinutes(1, 30), ScheduleDuration.between(LocalTime(9, 45), LocalTime(11, 15)))
    }
}
