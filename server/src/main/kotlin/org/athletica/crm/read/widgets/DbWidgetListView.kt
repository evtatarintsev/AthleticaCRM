package org.athletica.crm.read.widgets

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetDetailResponse
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetListItem
import org.athletica.crm.api.schemas.widgets.ScheduleWidgetListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.entityids.toScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.requireManageWidgets
import org.athletica.crm.core.widgets.WidgetHorizon
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [WidgetListView] поверх PostgreSQL со скоупом по `org_id` и `branch_id`. */
class DbWidgetListView : WidgetListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(): ScheduleWidgetListResponse {
        requireManageWidgets()
        return tr
            .sql(
                """
                SELECT id, name, enabled FROM schedule_widgets
                WHERE org_id = :orgId AND branch_id = :branchId
                ORDER BY name, id
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .list { row ->
                ScheduleWidgetListItem(id = row.asUuid("id").toScheduleWidgetId(), name = row.asString("name"), enabled = row.asBoolean("enabled"))
            }
            .let { ScheduleWidgetListResponse(it) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: ScheduleWidgetId): ScheduleWidgetDetailResponse {
        requireManageWidgets()
        return tr
            .sql(
                """
                SELECT w.id, w.name, w.public_key, w.enabled, w.horizon_days, w.show_coaches, w.show_hall,
                       w.show_disciplines,
                       (SELECT COALESCE(jsonb_agg(eg.group_id ORDER BY eg.group_id), '[]'::jsonb)
                          FROM schedule_widget_excluded_groups eg
                         WHERE eg.widget_id = w.id) AS excluded_group_ids
                FROM schedule_widgets w
                WHERE w.id = :id AND w.org_id = :orgId AND w.branch_id = :branchId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .firstOrNull { row -> row.toDetail() }
            ?: raise(CommonDomainError("WIDGET_NOT_FOUND", Messages.WidgetNotFound.localize()))
    }

    /** Собирает карточку виджета; исключённые группы декодируются сериализатором схемы. */
    private fun Row.toDetail(): ScheduleWidgetDetailResponse =
        ScheduleWidgetDetailResponse(
            id = asUuid("id").toScheduleWidgetId(),
            name = asString("name"),
            publicKey = asString("public_key"),
            enabled = asBoolean("enabled"),
            horizon = either { WidgetHorizon.from(asInt("horizon_days")) }.getOrElse { error(it) },
            showCoaches = asBoolean("show_coaches"),
            showHall = asBoolean("show_hall"),
            showDisciplines = asBoolean("show_disciplines"),
            excludedGroupIds = Json.decodeFromString(asString("excluded_group_ids")),
        )
}
