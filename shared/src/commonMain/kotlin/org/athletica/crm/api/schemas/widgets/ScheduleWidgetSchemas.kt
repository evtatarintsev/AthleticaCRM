package org.athletica.crm.api.schemas.widgets

import kotlinx.serialization.Serializable
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.widgets.WidgetHorizon

/** Запрос, адресующий один виджет: карточка, перевыпуск ключа, удаление. */
@Serializable
data class ScheduleWidgetIdRequest(
    /** Идентификатор виджета. */
    val id: ScheduleWidgetId,
)

/** Создание виджета в текущем филиале; незаданные настройки берутся по умолчанию. */
@Serializable
data class CreateScheduleWidgetRequest(
    /** Клиент-генерируемый идентификатор (UUIDv7). */
    val id: ScheduleWidgetId,
    /** Название виджета. */
    val name: NonBlankName,
    /** Открывается ли публичная страница. */
    val enabled: Boolean = true,
    /** Горизонт показа в днях. */
    val horizon: WidgetHorizon = WidgetHorizon.DEFAULT,
    /** Показывать тренеров. */
    val showCoaches: Boolean = false,
    /** Показывать зал. */
    val showHall: Boolean = true,
    /** Показывать дисциплины. */
    val showDisciplines: Boolean = true,
    /** Группы филиала, исключённые из виджета. */
    val excludedGroupIds: List<GroupId> = emptyList(),
)

/** Редактирование виджета: заменяет название и все настройки целиком. */
@Serializable
data class EditScheduleWidgetRequest(
    /** Идентификатор виджета. */
    val id: ScheduleWidgetId,
    /** Название виджета. */
    val name: NonBlankName,
    /** Открывается ли публичная страница. */
    val enabled: Boolean,
    /** Горизонт показа в днях. */
    val horizon: WidgetHorizon,
    /** Показывать тренеров. */
    val showCoaches: Boolean,
    /** Показывать зал. */
    val showHall: Boolean,
    /** Показывать дисциплины. */
    val showDisciplines: Boolean,
    /** Группы филиала, исключённые из виджета. */
    val excludedGroupIds: List<GroupId>,
)

/** Виджеты текущего филиала для раздела настроек. */
@Serializable
data class ScheduleWidgetListResponse(
    /** Виджеты по названию. */
    val widgets: List<ScheduleWidgetListItem>,
)

/** Строка списка виджетов. */
@Serializable
data class ScheduleWidgetListItem(
    /** Идентификатор виджета. */
    val id: ScheduleWidgetId,
    /** Название виджета. */
    val name: String,
    /** Открывается ли публичная страница. */
    val enabled: Boolean,
)

/** Карточка виджета с настройками и публичным ключом. */
@Serializable
data class ScheduleWidgetDetailResponse(
    /** Идентификатор виджета. */
    val id: ScheduleWidgetId,
    /** Название виджета. */
    val name: String,
    /** Публичный ключ из ссылки на страницу виджета. */
    val publicKey: String,
    /** Открывается ли публичная страница. */
    val enabled: Boolean,
    /** Горизонт показа в днях. */
    val horizon: WidgetHorizon,
    /** Показывать тренеров. */
    val showCoaches: Boolean,
    /** Показывать зал. */
    val showHall: Boolean,
    /** Показывать дисциплины. */
    val showDisciplines: Boolean,
    /** Группы филиала, исключённые из виджета. */
    val excludedGroupIds: List<GroupId>,
)
