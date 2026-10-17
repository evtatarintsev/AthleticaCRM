package org.athletica.crm.api.schemas.settings

import org.athletica.crm.core.entityids.DashboardWidgetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты дополнения настроек главной новыми типами виджетов. */
class DashboardSettingsTest {
    @Test
    fun `сохранённые до появления виджета незакрытых журналов настройки получают его в конце раскладки`() {
        val widgets =
            listOf(
                DashboardWidget.Debtors(DashboardWidgetId.new()),
                DashboardWidget.Sessions(DashboardWidgetId.new()),
                DashboardWidget.Birthdays(DashboardWidgetId.new()),
            )
        val settings = DashboardSettings(widgets = widgets, layout = widgets.map { it.id })

        val completed = settings.withDefaults()

        val added = assertIs<DashboardWidget.UnclosedSessions>(completed.widgets.last())
        assertEquals(settings.layout + added.id, completed.layout)
        assertEquals(DashboardWidget.UnclosedSessions.DEFAULT_LIMIT, added.limit)
    }
}
