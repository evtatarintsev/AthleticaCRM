package org.athletica.crm.core.errors

/** Запрос от заблокированного сотрудника: маршрут отвечает HTTP 401, как при недействительном токене. */
data object EmployeeBlocked : AppError {
    override val code = "EMPLOYEE_BLOCKED"
    override val message = "Employee is blocked"
}
