package org.athletica.crm.read.schedule

import org.athletica.crm.api.schemas.schedule.SessionColorKey
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Тесты запасного резолвера ключа палитры карточки занятия. */
class ScheduleColorTest {
    @Test
    fun `ключ стабилен между вызовами`() {
        val group = GroupId.new()
        val discipline = DisciplineId.new()
        assertEquals(colorKeyFor(group, discipline), colorKeyFor(group, discipline))
        assertEquals(colorKeyFor(group, null), colorKeyFor(group, null))
    }

    @Test
    fun `ключ зависит только от битов UUID`() {
        val discipline = DisciplineId(Uuid.parse("00000000-0000-7000-8000-000000000003"))
        assertEquals(SessionColorKey.palette[3], colorKeyFor(GroupId.new(), discipline))
    }

    @Test
    fun `занятия одной дисциплины получают одинаковый ключ независимо от группы`() {
        val discipline = DisciplineId.new()
        val keys = (1..20).map { colorKeyFor(GroupId.new(), discipline) }.toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun `занятие без дисциплины получает ключ по группе`() {
        val group = GroupId(Uuid.parse("00000000-0000-7000-8000-000000000005"))
        assertEquals(SessionColorKey.palette[5], colorKeyFor(group, null))
    }

    @Test
    fun `ключ всегда из палитры`() {
        repeat(200) {
            assertTrue(colorKeyFor(GroupId.new(), null) in SessionColorKey.palette)
        }
    }
}
