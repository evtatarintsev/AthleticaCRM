package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Учёт последней активности сотрудников: пишет `employees.last_seen_at` не чаще раза в минуту
 * на сотрудника, помня момент последней записи в памяти процесса.
 */
class EmployeePresence {
    private val lastWritten = ConcurrentHashMap<EmployeeId, Instant>()

    /** Отмечает активность сотрудника [employeeId] в момент [now], если с прошлой записи прошла минута. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun touch(employeeId: EmployeeId, now: Instant) {
        val previous = lastWritten[employeeId]
        if (previous != null && previous > now - THROTTLE) {
            return
        }
        tr.sql("UPDATE employees SET last_seen_at = :now WHERE id = :id")
            .bind("now", now)
            .bind("id", employeeId)
            .execute()
        lastWritten[employeeId] = now
    }

    private companion object {
        /** Минимальный интервал между записями активности одного сотрудника. */
        val THROTTLE = 1.minutes
    }
}
