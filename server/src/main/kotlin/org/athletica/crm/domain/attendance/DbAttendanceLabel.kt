package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация метки посещаемости. Все запросы фильтруются по `org_id`. */
data class DbAttendanceLabel(
    override val id: AttendanceLabelId,
    override val name: String,
    override val scope: AttendanceLabelScope,
    override val position: Int,
    override val isArchived: Boolean,
) : AttendanceLabel {
    override fun renamed(name: String): AttendanceLabel = copy(name = name.trim())

    override fun movedTo(position: Int): AttendanceLabel = copy(position = position)

    override fun archived(): AttendanceLabel = copy(isArchived = true)

    override fun restored(): AttendanceLabel = copy(isArchived = false)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        if (name.isBlank()) {
            raise(CommonDomainError("ATTENDANCE_LABEL_NAME_BLANK", Messages.AttendanceLabelNameBlank.localize()))
        }
        try {
            tr
                .sql(
                    """
                    INSERT INTO attendance_labels (id, org_id, name, scope, position, archived_at)
                    VALUES (:id, :orgId, :name, :scope::attendance_label_scope, :position,
                            CASE WHEN :archived THEN now() END)
                    ON CONFLICT (id) DO UPDATE
                        SET name = EXCLUDED.name,
                            position = EXCLUDED.position,
                            archived_at = CASE
                                WHEN NOT :archived THEN NULL
                                ELSE COALESCE(attendance_labels.archived_at, now())
                            END
                    WHERE attendance_labels.org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .bind("scope", scope.toDb())
                .bind("position", position)
                .bind("archived", isArchived)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("ATTENDANCE_LABEL_ALREADY_EXISTS", Messages.AttendanceLabelAlreadyExists.localize()))
        }
    }
}

/** Значение enum-типа `attendance_label_scope` в БД. */
internal fun AttendanceLabelScope.toDb(): String = name.lowercase()

/** [AttendanceLabelScope] из значения enum-типа `attendance_label_scope` в БД. */
internal fun attendanceLabelScopeOf(db: String): AttendanceLabelScope = AttendanceLabelScope.valueOf(db.uppercase())
