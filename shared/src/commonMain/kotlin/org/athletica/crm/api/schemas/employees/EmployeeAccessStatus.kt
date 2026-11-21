package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable

/** Статус доступа сотрудника к системе, вычисляемый из его данных. */
@Serializable
enum class EmployeeAccessStatus {
    /** Логин не выдан. */
    NO_ACCESS,

    /** Логин выдан, активности ещё не было. */
    INVITED,

    /** Логин выдан и была активность. */
    ACTIVE,

    /** Сотрудник заблокирован. */
    BLOCKED,
}
