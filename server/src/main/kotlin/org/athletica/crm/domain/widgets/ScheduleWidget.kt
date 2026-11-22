package org.athletica.crm.domain.widgets

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.widgets.WidgetHorizon
import org.athletica.crm.storage.Transaction

/**
 * Виджет расписания филиала: публичная страница с занятиями для сайта клуба и ВКонтакте.
 * Все команды требуют права `CAN_MANAGE_WIDGETS`, иначе ошибка `PERMISSION_DENIED`.
 */
interface ScheduleWidget {
    /** Идентификатор виджета. */
    val id: ScheduleWidgetId

    /** Филиал, занятия которого показывает виджет. */
    val branchId: BranchId

    /** Название виджета для списка в настройках. */
    val name: String

    /** Публичный ключ из ссылки на страницу виджета. */
    val publicKey: String

    /** Настройки показа. */
    val settings: WidgetSettings

    /**
     * Сохраняет виджет: INSERT при создании либо UPDATE существующего.
     * Ошибка `GROUP_NOT_FOUND`, если среди исключённых есть группа не из филиала виджета.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save()

    /** Заменяет публичный ключ новым случайным; старая ссылка перестаёт открывать виджет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun regenerateKey()

    /** Удаляет виджет вместе со списком исключённых групп. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun delete()

    /** Возвращает копию с новыми названием [name] и настройками [settings] (без записи в БД). */
    fun withNew(name: NonBlankName, settings: WidgetSettings): ScheduleWidget
}

/** Настройки показа виджета; значения по умолчанию — настройки нового виджета. */
data class WidgetSettings(
    /** Открывается ли публичная страница виджета. */
    val enabled: Boolean = true,
    /** На сколько дней вперёд от сегодня видны занятия. */
    val horizon: WidgetHorizon = WidgetHorizon.DEFAULT,
    /** Показывать тренеров занятия. */
    val showCoaches: Boolean = false,
    /** Показывать зал занятия. */
    val showHall: Boolean = true,
    /** Показывать дисциплины группы. */
    val showDisciplines: Boolean = true,
    /** Группы филиала, занятия которых виджет не показывает. */
    val excludedGroups: List<GroupId> = emptyList(),
)
