package org.athletica.crm.domain.widgets

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.widgets.WidgetHorizon
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация [ScheduleWidgets]. */
class DbScheduleWidgets : ScheduleWidgets {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(id: ScheduleWidgetId, name: NonBlankName, settings: WidgetSettings): ScheduleWidget = DbScheduleWidget(id, ctx.branchId, name.value, newWidgetKey(), settings)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: ScheduleWidgetId): ScheduleWidget {
        val excluded =
            tr.sql("SELECT group_id FROM schedule_widget_excluded_groups WHERE widget_id = :id ORDER BY group_id")
                .bind("id", id)
                .list { it.asUuid("group_id").toGroupId() }
        return tr
            .sql(
                """
                SELECT name, public_key, enabled, horizon_days, show_coaches, show_hall, show_disciplines
                FROM schedule_widgets
                WHERE id = :id AND org_id = :orgId AND branch_id = :branchId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .firstOrNull { row ->
                DbScheduleWidget(
                    id = id,
                    branchId = ctx.branchId,
                    name = row.asString("name"),
                    publicKey = row.asString("public_key"),
                    settings =
                        WidgetSettings(
                            enabled = row.asBoolean("enabled"),
                            horizon = either { WidgetHorizon.from(row.asInt("horizon_days")) }.getOrElse { error(it) },
                            showCoaches = row.asBoolean("show_coaches"),
                            showHall = row.asBoolean("show_hall"),
                            showDisciplines = row.asBoolean("show_disciplines"),
                            excludedGroups = excluded,
                        ),
                )
            }
            ?: raise(CommonDomainError("WIDGET_NOT_FOUND", Messages.WidgetNotFound.localize()))
    }
}
