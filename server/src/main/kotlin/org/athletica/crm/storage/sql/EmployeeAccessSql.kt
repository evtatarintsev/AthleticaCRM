package org.athletica.crm.storage.sql

/**
 * Состояния доступа сотрудника (`EmployeeAccess` в домене) — условия над таблицей `employees`
 * с псевдонимом `e`. Одно определение для домена, проекций, авторизации и CLI;
 * пакет нейтральный, не зависит ни от `domain/`, ни от `read/`.
 */
object EmployeeAccessSql {
    /** Сотрудник может работать в системе: логин выдан и сотрудник не заблокирован. */
    const val ACTIVE = "(e.user_id IS NOT NULL AND e.blocked_at IS NULL)"

    /** Сотрудник заблокирован, с логином или без него. */
    const val BLOCKED = "(e.blocked_at IS NOT NULL)"

    /** Логин сотруднику ещё не выдан. */
    const val NO_LOGIN = "(e.user_id IS NULL)"
}
