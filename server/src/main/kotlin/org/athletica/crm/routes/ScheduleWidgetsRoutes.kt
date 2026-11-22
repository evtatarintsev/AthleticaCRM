package org.athletica.crm.routes

import org.athletica.crm.api.schemas.widgets.CreateScheduleWidgetRequest
import org.athletica.crm.api.schemas.widgets.EditScheduleWidgetRequest
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetDetailResponse
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetIdRequest
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetListResponse
import org.athletica.crm.domain.widgets.ScheduleWidgets
import org.athletica.crm.domain.widgets.WidgetSettings
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database

/** Регистрирует маршруты управления виджетами расписания текущего филиала. */
context(db: Database)
fun RouteWithContext.scheduleWidgetsRoutes(widgets: ScheduleWidgets, views: ReadViews) {
    route("/schedule-widgets") {
        get<Unit, ScheduleWidgetListResponse>("/list") {
            db.transaction {
                views.widgetList.list()
            }
        }

        get<ScheduleWidgetIdRequest, ScheduleWidgetDetailResponse>("/detail") { request ->
            db.transaction {
                views.widgetList.byId(request.id)
            }
        }

        post<CreateScheduleWidgetRequest, ScheduleWidgetDetailResponse>("/create") { request ->
            db.transaction {
                widgets.new(request.id, request.name, request.settings()).save()
                views.widgetList.byId(request.id)
            }
        }

        post<EditScheduleWidgetRequest, ScheduleWidgetDetailResponse>("/edit") { request ->
            db.transaction {
                widgets.byId(request.id).withNew(request.name, request.settings()).save()
                views.widgetList.byId(request.id)
            }
        }

        post<ScheduleWidgetIdRequest, ScheduleWidgetDetailResponse>("/regenerate-key") { request ->
            db.transaction {
                widgets.byId(request.id).regenerateKey()
                views.widgetList.byId(request.id)
            }
        }

        post<ScheduleWidgetIdRequest, Unit>("/delete") { request ->
            db.transaction {
                widgets.byId(request.id).delete()
            }
        }
    }
}

/** Настройки нового виджета из запроса создания. */
private fun CreateScheduleWidgetRequest.settings() = WidgetSettings(enabled, horizon, showCoaches, showHall, showDisciplines, excludedGroupIds)

/** Настройки виджета из запроса редактирования. */
private fun EditScheduleWidgetRequest.settings() = WidgetSettings(enabled, horizon, showCoaches, showHall, showDisciplines, excludedGroupIds)
