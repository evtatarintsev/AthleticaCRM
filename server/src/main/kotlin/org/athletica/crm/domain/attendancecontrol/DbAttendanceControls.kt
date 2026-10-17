package org.athletica.crm.domain.attendancecontrol

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt

/** Репозиторий настроек контроля посещаемости поверх PostgreSQL. */
class DbAttendanceControls : AttendanceControls {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): AttendanceControl =
        tr
            .sql(
                """
                SELECT overdue_task_enabled, overdue_task_delay_minutes
                FROM attendance_control_settings
                WHERE org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                val minutes = row.asInt("overdue_task_delay_minutes")
                DbAttendanceControl(
                    overdueTaskEnabled = row.asBoolean("overdue_task_enabled"),
                    overdueTaskDelay =
                        JournalCloseDelay.from(minutes).getOrElse {
                            error("Недопустимое окно закрытия журнала в БД: $minutes")
                        },
                )
            }
            ?: DbAttendanceControl(AttendanceControl.DEFAULT_OVERDUE_TASK_ENABLED, JournalCloseDelay.DEFAULT)
}
