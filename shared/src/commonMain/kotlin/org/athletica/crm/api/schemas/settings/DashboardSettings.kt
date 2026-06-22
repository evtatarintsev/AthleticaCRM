package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.DashboardWidgetId

/**
 * Виджет главной страницы — сущность с собственной идентичностью и настройками.
 * Видимостью и порядком виджет НЕ владеет: это concern размещения ([DashboardSettings.layout]),
 * уровнем выше виджета. Sealed-иерархия сериализуется полиморфно с дискриминатором `type`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface DashboardWidget {
    /** Идентификатор экземпляра. Позволяет иметь несколько виджетов одного типа. */
    val id: DashboardWidgetId

    /**
     * Пользовательский заголовок виджета на экране. `null` или пустая строка — использовать
     * локализованное имя типа виджета по умолчанию. Непустое значение — переименование.
     */
    val title: String?

    /** «Занятия сегодня». Кроме заголовка, настроек пока нет. */
    @Serializable
    @SerialName("sessions")
    data class Sessions(
        override val id: DashboardWidgetId,
        override val title: String? = null,
    ) : DashboardWidget

    /** «Должники»: [limit] — максимум строк в виджете. */
    @Serializable
    @SerialName("debtors")
    data class Debtors(
        override val id: DashboardWidgetId,
        override val title: String? = null,
        val limit: Int = DEFAULT_LIMIT,
    ) : DashboardWidget {
        companion object {
            const val DEFAULT_LIMIT = 10
        }
    }

    /** «Дни рождения»: [window] — окно отбора именинников, [limit] — максимум строк. */
    @Serializable
    @SerialName("birthdays")
    data class Birthdays(
        override val id: DashboardWidgetId,
        override val title: String? = null,
        val window: BirthdayWindow = BirthdayWindow.TODAY,
        val limit: Int = DEFAULT_LIMIT,
    ) : DashboardWidget {
        companion object {
            const val DEFAULT_LIMIT = 10
        }
    }
}

/** Окно отбора именинников относительно текущей даты. */
@Serializable
enum class BirthdayWindow {
    /** Только сегодня. */
    TODAY,

    /** Только завтра. */
    TOMORROW,

    /** Ближайшая неделя (сегодня — сегодня+7). */
    WEEK,
}

/**
 * Настройки дашборда главной страницы.
 * [widgets] — пул сконфигурированных экземпляров виджетов (вся конфигурация живёт здесь).
 * [layout] — идентификаторы показанных виджетов в порядке отображения; видимость =
 * членство в [layout], порядок = порядок в [layout]. Скрытый виджет остаётся в [widgets]
 * (сохраняет настройки), но отсутствует в [layout].
 */
@Serializable
data class DashboardSettings(
    val widgets: List<DashboardWidget> = emptyList(),
    val layout: List<DashboardWidgetId> = emptyList(),
) {
    /** Видимые виджеты в порядке [layout]. Идентификаторы без виджета в пуле игнорируются. */
    fun orderedVisible(): List<DashboardWidget> {
        val byId = widgets.associateBy { it.id }
        return layout.mapNotNull { byId[it] }
    }

    /** Скрытые виджеты: присутствуют в [widgets], но отсутствуют в [layout]. */
    fun hidden(): List<DashboardWidget> {
        val shown = layout.toSet()
        return widgets.filterNot { it.id in shown }
    }

    companion object {
        /**
         * Канонический дефолтный набор: по одному экземпляру каждого типа виджета,
         * все показаны в порядке объявления. Идентификаторы генерируются при каждом вызове.
         */
        fun default(): DashboardSettings {
            val widgets =
                listOf(
                    DashboardWidget.Sessions(DashboardWidgetId.new()),
                    DashboardWidget.Debtors(DashboardWidgetId.new()),
                    DashboardWidget.Birthdays(DashboardWidgetId.new()),
                )
            return DashboardSettings(widgets = widgets, layout = widgets.map { it.id })
        }
    }
}

/**
 * Дополняет настройки недостающими дефолтными виджетами. Пустой пул заменяется полным
 * [DashboardSettings.default]; иначе для каждого типа из дефолтного набора, отсутствующего
 * в пуле, добавляется его экземпляр в конец [DashboardSettings.widgets] и [DashboardSettings.layout].
 * Покрывает нового пользователя (пустые настройки) и типы виджетов, добавленные в код позже.
 */
fun DashboardSettings.withDefaults(): DashboardSettings {
    if (widgets.isEmpty()) {
        return DashboardSettings.default()
    }
    val presentTypes = widgets.map { it::class }.toSet()
    val missing = DashboardSettings.default().widgets.filterNot { it::class in presentTypes }
    if (missing.isEmpty()) {
        return this
    }
    return copy(widgets = widgets + missing, layout = layout + missing.map { it.id })
}
