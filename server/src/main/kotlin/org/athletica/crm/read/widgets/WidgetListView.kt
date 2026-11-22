package org.athletica.crm.read.widgets

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetDetailResponse
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция раздела настроек «Виджеты расписания»: виджеты текущего филиала.
 * Требует права `CAN_MANAGE_WIDGETS`, иначе ошибка `PERMISSION_DENIED`.
 */
interface WidgetListView {
    /** Возвращает виджеты текущего филиала по названию. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun list(): ScheduleWidgetListResponse

    /** Возвращает карточку виджета [id] текущего филиала; ошибка `WIDGET_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: ScheduleWidgetId): ScheduleWidgetDetailResponse
}
