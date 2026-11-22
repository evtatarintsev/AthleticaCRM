package org.athletica.crm.domain.widgets

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.requireManageWidgets
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong

/** R2DBC-реализация виджета. Все запросы ограничены `org_id` и филиалом виджета. */
internal data class DbScheduleWidget(
    override val id: ScheduleWidgetId,
    override val branchId: BranchId,
    override val name: String,
    override val publicKey: String,
    override val settings: WidgetSettings,
) : ScheduleWidget {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        requireManageWidgets()
        requireBranchGroups()
        val saved =
            tr.sql(
                """
                INSERT INTO schedule_widgets (id, org_id, branch_id, name, public_key, enabled, horizon_days,
                                              show_coaches, show_hall, show_disciplines)
                VALUES (:id, :orgId, :branchId, :name, :publicKey, :enabled, :horizonDays,
                        :showCoaches, :showHall, :showDisciplines)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name, enabled = EXCLUDED.enabled, horizon_days = EXCLUDED.horizon_days,
                    show_coaches = EXCLUDED.show_coaches, show_hall = EXCLUDED.show_hall,
                    show_disciplines = EXCLUDED.show_disciplines
                WHERE schedule_widgets.org_id = :orgId AND schedule_widgets.branch_id = :branchId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("branchId", branchId)
                .bind("name", name)
                .bind("publicKey", publicKey)
                .bind("enabled", settings.enabled)
                .bind("horizonDays", settings.horizon.days)
                .bind("showCoaches", settings.showCoaches)
                .bind("showHall", settings.showHall)
                .bind("showDisciplines", settings.showDisciplines)
                .execute()
        if (saved == 0L) {
            raise(CommonDomainError("WIDGET_NOT_FOUND", Messages.WidgetNotFound.localize()))
        }

        tr.sql("DELETE FROM schedule_widget_excluded_groups WHERE widget_id = :id")
            .bind("id", id)
            .execute()
        if (settings.excludedGroups.isNotEmpty()) {
            tr.sql(
                """
                INSERT INTO schedule_widget_excluded_groups (widget_id, group_id)
                SELECT :id, g FROM unnest(:groupIds::uuid[]) AS g
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("groupIds", settings.excludedGroups.distinct())
                .execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun regenerateKey() {
        requireManageWidgets()
        tr.sql("UPDATE schedule_widgets SET public_key = :key WHERE id = :id AND org_id = :orgId AND branch_id = :branchId")
            .bind("key", newWidgetKey())
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", branchId)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun delete() {
        requireManageWidgets()
        tr.sql("DELETE FROM schedule_widgets WHERE id = :id AND org_id = :orgId AND branch_id = :branchId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", branchId)
            .execute()
    }

    override fun withNew(name: NonBlankName, settings: WidgetSettings): ScheduleWidget = copy(name = name.value, settings = settings)

    /** Проверяет, что все исключённые группы относятся к филиалу виджета. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun requireBranchGroups() {
        val ids = settings.excludedGroups.distinct()
        if (ids.isEmpty()) {
            return
        }
        val found =
            tr.sql("SELECT COUNT(*) AS cnt FROM groups WHERE id = ANY(:ids) AND org_id = :orgId AND branch_id = :branchId")
                .bind("ids", ids)
                .bind("orgId", ctx.orgId)
                .bind("branchId", branchId)
                .firstOrNull { it.asLong("cnt") } ?: 0L
        if (found != ids.size.toLong()) {
            raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        }
    }
}
