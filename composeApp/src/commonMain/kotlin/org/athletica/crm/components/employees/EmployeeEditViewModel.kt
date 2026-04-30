package org.athletica.crm.components.employees

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.UpdateEmployeeRequest
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.pickImageFile

/**
 * ViewModel экрана редактирования сотрудника.
 * Загружает данные сотрудника и список ролей, обрабатывает загрузку аватара и сохранение.
 * При успехе вызывает [onSaved].
 */
class EmployeeEditViewModel(
    private val api: ApiClient,
    private val employeeId: EmployeeId,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    var loadState by mutableStateOf<EmployeeEditLoadState>(EmployeeEditLoadState.Loading)
        private set

    var saveState by mutableStateOf<EmployeeSaveState>(EmployeeSaveState.Idle)
        private set

    /** Идентификатор нового аватара (null — использовать исходный). */
    var avatarId by mutableStateOf<UploadId?>(null)
        private set

    /** Прямая ссылка на новый аватар для предпросмотра. */
    var avatarUrl by mutableStateOf<String?>(null)
        private set

    /** Признак выполняющейся загрузки аватара. */
    var isUploadingAvatar by mutableStateOf(false)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            loadState = EmployeeEditLoadState.Loading
            val employeeResult = api.employeeDetail(employeeId)
            val rolesResult = api.roles()
            val branchesResult = api.listBranches()
            loadState =
                when {
                    employeeResult.isLeft() ->
                        EmployeeEditLoadState.Error(employeeResult.leftOrNull()!!.toEmployeesApiError())
                    rolesResult.isLeft() ->
                        EmployeeEditLoadState.Error(rolesResult.leftOrNull()!!.toEmployeesApiError())
                    branchesResult.isLeft() ->
                        EmployeeEditLoadState.Error(branchesResult.leftOrNull()!!.toEmployeesApiError())
                    else ->
                        EmployeeEditLoadState.Loaded(
                            employee = employeeResult.getOrNull()!!,
                            roles = rolesResult.getOrNull()!!.roles,
                            branches = branchesResult.getOrNull()!!.branches,
                        )
                }
        }
    }

    /** Открывает файл-пикер, загружает выбранное изображение как аватар. */
    fun onUploadAvatar() {
        scope.launch {
            val file = pickImageFile() ?: return@launch
            isUploadingAvatar = true
            api.uploadFile(file.first, file.second, file.third).fold(
                ifLeft = { saveState = EmployeeSaveState.Error(it.toEmployeesApiError()) },
                ifRight = { upload ->
                    avatarId = upload.id
                    avatarUrl = upload.url
                },
            )
            isUploadingAvatar = false
        }
    }

    /** Сохраняет изменения формы [form]. */
    fun onSave(form: EmployeeForm) {
        val employee = (loadState as? EmployeeEditLoadState.Loaded)?.employee ?: return
        scope.launch {
            saveState = EmployeeSaveState.Saving
            api
                .updateEmployee(
                    UpdateEmployeeRequest(
                        id = employee.id,
                        name = form.name.trim(),
                        phoneNo = form.phoneNo.trim().ifBlank { null },
                        email = form.email.trim().ifBlank { null }?.toEmailAddress(),
                        avatarId = avatarId ?: employee.avatarId,
                        roleIds = form.selectedRoleIds.toList(),
                        grantedPermissions = form.grantedPermissions,
                        revokedPermissions = form.revokedPermissions,
                        allBranchesAccess = form.allBranchesAccess,
                        branchIds = form.selectedBranchIds.toList(),
                    ),
                ).fold(
                    ifLeft = { saveState = EmployeeSaveState.Error(it.toEmployeesApiError()) },
                    ifRight = {
                        saveState = EmployeeSaveState.Idle
                        onSaved()
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        saveState = EmployeeSaveState.Idle
    }
}
