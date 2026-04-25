package org.athletica.crm.components.employees

import org.athletica.crm.core.permissions.Permission
import kotlin.uuid.Uuid

/**
 * Состояние формы создания / редактирования сотрудника.
 * [isValid] — форма заполнена достаточно для отправки.
 */
data class EmployeeForm(
    val name: String = "",
    val phoneNo: String = "",
    val email: String = "",
    val selectedRoleIds: Set<Uuid> = emptySet(),
    val grantedPermissions: Set<Permission> = emptySet(),
    val revokedPermissions: Set<Permission> = emptySet(),
) {
    val isValid: Boolean get() = name.isNotBlank() && email.isNotBlank()
}

/** Состояние операции сохранения (создания или обновления) сотрудника. */
sealed class EmployeeSaveState {
    /** Форма ожидает отправки. */
    data object Idle : EmployeeSaveState()

    /** Запрос выполняется. */
    data object Saving : EmployeeSaveState()

    /** Сервер вернул ошибку. */
    data class Error(val error: EmployeesApiError) : EmployeeSaveState()
}

/** Состояние загрузки данных при инициализации экрана создания (список ролей). */
sealed class EmployeeCreateLoadState {
    /** Загрузка в процессе. */
    data object Loading : EmployeeCreateLoadState()

    /** Роли загружены и форма готова к заполнению. */
    data class Loaded(
        val roles: List<org.athletica.crm.api.schemas.employees.RoleItem>,
    ) : EmployeeCreateLoadState()

    /** Ошибка загрузки ролей. */
    data class Error(val error: EmployeesApiError) : EmployeeCreateLoadState()
}

/** Состояние загрузки данных при инициализации экрана редактирования (сотрудник + список ролей). */
sealed class EmployeeEditLoadState {
    /** Загрузка в процессе. */
    data object Loading : EmployeeEditLoadState()

    /**
     * Данные загружены.
     * [employee] — текущие данные сотрудника; [roles] — доступные роли.
     */
    data class Loaded(
        val employee: org.athletica.crm.api.schemas.employees.EmployeeDetailResponse,
        val roles: List<org.athletica.crm.api.schemas.employees.RoleItem>,
    ) : EmployeeEditLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: EmployeesApiError) : EmployeeEditLoadState()
}
