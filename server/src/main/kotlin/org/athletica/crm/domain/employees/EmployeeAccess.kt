package org.athletica.crm.domain.employees

import org.athletica.crm.core.entityids.UserId
import kotlin.time.Instant

/**
 * Доступ сотрудника к системе — явное состояние вместо пары «логин + момент блокировки».
 *
 * Переходы (`Employee.invite`/`block`/`unblock`):
 * - [NoLogin] → [Active] — выдача логина;
 * - [NoLogin] или [Active] → [Blocked] — блокировка;
 * - [Blocked] → [Active] или [NoLogin] — снятие блокировки, логин сохраняется.
 *
 * Заблокированный без логина допустим: сотрудника, которому доступ ещё не выдан,
 * можно заблокировать заранее (например, уволенного), и пригласить его нельзя, пока блокировку не снимут.
 * SQL-определение тех же состояний — `storage.sql.EmployeeAccessSql`.
 */
sealed interface EmployeeAccess {
    /** Логин сотрудника; `null` — логин не выдан. */
    val userId: UserId?

    /** Логин не выдан, войти сотрудник не может. */
    data object NoLogin : EmployeeAccess {
        override val userId: UserId? = null
    }

    /** Логин выдан, сотрудник работает в системе. */
    data class Active(override val userId: UserId) : EmployeeAccess

    /** Сотрудник заблокирован в момент [blockedAt]; логин [userId], если был выдан, сохраняется. */
    data class Blocked(
        override val userId: UserId?,
        /** Момент блокировки. */
        val blockedAt: Instant,
    ) : EmployeeAccess

    companion object {
        /** Состояние по хранимым логину [userId] и моменту блокировки [blockedAt]. */
        fun of(userId: UserId?, blockedAt: Instant?): EmployeeAccess =
            when {
                blockedAt != null -> Blocked(userId, blockedAt)
                userId != null -> Active(userId)
                else -> NoLogin
            }
    }
}
