package org.athletica.crm.domain.attendancecontrol

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Настройки контроля посещаемости, сохраняемые в `attendance_control_settings`. */
data class DbAttendanceControl(
    override val overdueTaskEnabled: Boolean,
    override val overdueTaskDelay: JournalCloseDelay,
) : AttendanceControl {
    override fun withNew(enabled: Boolean, delay: JournalCloseDelay): AttendanceControl = copy(overdueTaskEnabled = enabled, overdueTaskDelay = delay)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr
            .sql(
                """
                INSERT INTO attendance_control_settings (org_id, overdue_task_enabled, overdue_task_delay_minutes, updated_at)
                VALUES (:orgId, :enabled, :delayMinutes, now())
                ON CONFLICT (org_id) DO UPDATE
                SET overdue_task_enabled = EXCLUDED.overdue_task_enabled,
                    overdue_task_delay_minutes = EXCLUDED.overdue_task_delay_minutes,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("enabled", overdueTaskEnabled)
            .bind("delayMinutes", overdueTaskDelay.minutes)
            .execute()
    }
}
