package org.athletica.crm.domain.notifications

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты текстов и хранения уведомления [GroupScheduleChanged]. */
class GroupScheduleChangedTest {
    private val content =
        GroupScheduleChanged(
            groupId = GroupId.new(),
            groupName = "Йога",
            effectiveFrom = LocalDate(2026, 10, 12),
            changedByName = "Анна Иванова",
        )

    @Test
    fun `текст собирается на русском`() {
        assertEquals("Изменено расписание группы «Йога»", content.title(Lang.RU))
        assertEquals("Новое расписание действует с 12.10.2026. Автор изменения: Анна Иванова", content.body(Lang.RU))
    }

    @Test
    fun `текст собирается на английском`() {
        assertEquals("Schedule changed for group “Йога”", content.title(Lang.EN))
        assertEquals("The new schedule takes effect on Oct 12, 2026. Changed by Анна Иванова", content.body(Lang.EN))
    }

    @Test
    fun `ведёт на группу`() {
        assertEquals(NotificationSubject.Group(content.groupId), content.subject)
    }

    @Test
    fun `переживает сохранение в JSON`() {
        val restored = Json.decodeFromString<NotificationContent>(Json.encodeToString<NotificationContent>(content))

        assertEquals(content, restored)
    }
}
