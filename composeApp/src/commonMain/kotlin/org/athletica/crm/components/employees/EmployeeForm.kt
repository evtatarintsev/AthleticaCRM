package org.athletica.crm.components.employees

import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.core.entityids.BranchId
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
    /** true — доступ ко всем филиалам. */
    val allBranchesAccess: Boolean = true,
    /** Выбранные филиалы; учитывается только когда [allBranchesAccess] = false. */
    val selectedBranchIds: Set<BranchId> = emptySet(),
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

/** Состояние загрузки данных при инициализации экрана создания (список ролей и филиалов). */
sealed class EmployeeCreateLoadState {
    /** Загрузка в процессе. */
    data object Loading : EmployeeCreateLoadState()

    /** Роли и филиалы загружены, форма готова к заполнению. */
    data class Loaded(
        val roles: List<RoleItem>,
        val branches: List<BranchDetailResponse>,
    ) : EmployeeCreateLoadState()

    /** Ошибка загрузки ролей или филиалов. */
    data class Error(val error: EmployeesApiError) : EmployeeCreateLoadState()
}

/** Состояние загрузки данных при инициализации экрана редактирования (сотрудник + роли + филиалы). */
sealed class EmployeeEditLoadState {
    /** Загрузка в процессе. */
    data object Loading : EmployeeEditLoadState()

    /**
     * Данные загружены.
     * [employee] — текущие данные сотрудника; [roles] — доступные роли; [branches] — все филиалы организации.
     */
    data class Loaded(
        val employee: org.athletica.crm.api.schemas.employees.EmployeeDetailResponse,
        val roles: List<RoleItem>,
        val branches: List<BranchDetailResponse>,
    ) : EmployeeEditLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: EmployeesApiError) : EmployeeEditLoadState()
}
