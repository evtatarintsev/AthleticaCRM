package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.toAttendanceLabelId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация справочника меток посещаемости. */
class DbAttendanceLabels : AttendanceLabels {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(includeArchived: Boolean): List<AttendanceLabel> =
        tr
            .sql(
                """
                SELECT $COLUMNS
                FROM attendance_labels l
                WHERE l.org_id = :orgId AND (:includeArchived OR l.archived_at IS NULL)
                ORDER BY l.position, l.name
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("includeArchived", includeArchived)
            .list { it.toLabel() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: AttendanceLabelId, name: String, scope: AttendanceLabelScope): AttendanceLabel {
        val position =
            tr
                .sql("SELECT COALESCE(MAX(position), 0) + 1 AS next FROM attendance_labels WHERE org_id = :orgId")
                .bind("orgId", ctx.orgId)
                .firstOrNull { it.asInt("next") } ?: 1
        return DbAttendanceLabel(id = id, name = name.trim(), scope = scope, position = position, isArchived = false)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: AttendanceLabelId): AttendanceLabel = byIds(listOf(id)).single()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: Collection<AttendanceLabelId>): List<AttendanceLabel> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }
        val labels =
            tr
                .sql("SELECT $COLUMNS FROM attendance_labels l WHERE l.id = ANY(:ids) AND l.org_id = :orgId ORDER BY l.position, l.name")
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list { it.toLabel() }
        if (labels.size != distinctIds.size) {
            raise(CommonDomainError("ATTENDANCE_LABEL_NOT_FOUND", Messages.AttendanceLabelNotFound.localize()))
        }
        return labels
    }

    private fun Row.toLabel(): AttendanceLabel =
        DbAttendanceLabel(
            id = asUuid("id").toAttendanceLabelId(),
            name = asString("name"),
            scope = attendanceLabelScopeOf(asString("scope")),
            position = asInt("position"),
            isArchived = asBoolean("is_archived"),
        )

    private companion object {
        /** Колонки метки под псевдонимом `l`. */
        const val COLUMNS = "l.id, l.name, l.scope::text AS scope, l.position, l.archived_at IS NOT NULL AS is_archived"
    }
}
