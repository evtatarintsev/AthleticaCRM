package org.athletica.crm.domain.widgets

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Виджеты расписания текущего филиала сотрудника. */
interface ScheduleWidgets {
    /** Создаёт несохранённый виджет со случайным публичным ключом; запись в БД — через [ScheduleWidget.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun new(id: ScheduleWidgetId, name: NonBlankName, settings: WidgetSettings = WidgetSettings()): ScheduleWidget

    /** Возвращает виджет текущего филиала; ошибка `WIDGET_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: ScheduleWidgetId): ScheduleWidget
}
