package org.athletica.crm.components.employees

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.pickImageFile

/**
 * ViewModel экрана создания сотрудника.
 * Загружает список ролей, обрабатывает загрузку аватара и создание сотрудника.
 * При успехе вызывает [onCreated].
 */
class EmployeeCreateViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onCreated: () -> Unit,
) {
    var loadState by mutableStateOf<EmployeeCreateLoadState>(EmployeeCreateLoadState.Loading)
        private set

    var saveState by mutableStateOf<EmployeeSaveState>(EmployeeSaveState.Idle)
        private set

    /** Идентификатор загруженного аватара. */
    var avatarId by mutableStateOf<UploadId?>(null)
        private set

    /** Прямая ссылка на загруженный аватар для предпросмотра. */
    var avatarUrl by mutableStateOf<String?>(null)
        private set

    /** Признак выполняющейся загрузки аватара. */
    var isUploadingAvatar by mutableStateOf(false)
        private set

    init {
        loadRoles()
    }

    private fun loadRoles() {
        scope.launch {
            loadState = EmployeeCreateLoadState.Loading
            api.roles().fold(
                ifLeft = { loadState = EmployeeCreateLoadState.Error(it.toEmployeesApiError()) },
                ifRight = { loadState = EmployeeCreateLoadState.Loaded(it.roles) },
            )
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

    /** Создаёт сотрудника по данным [form]. */
    fun onCreate(form: EmployeeForm) {
        scope.launch {
            saveState = EmployeeSaveState.Saving
            api
                .createEmployee(
                    CreateEmployeeRequest(
                        id = EmployeeId.new(),
                        name = form.name.trim(),
                        phoneNo = form.phoneNo.trim().ifBlank { null },
                        email = form.email.trim().ifBlank { null }?.toEmailAddress(),
                        avatarId = avatarId,
                        roleIds = form.selectedRoleIds.toList(),
                        grantedPermissions = form.grantedPermissions,
                        revokedPermissions = form.revokedPermissions,
                    ),
                ).fold(
                    ifLeft = { saveState = EmployeeSaveState.Error(it.toEmployeesApiError()) },
                    ifRight = {
                        saveState = EmployeeSaveState.Idle
                        onCreated()
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        saveState = EmployeeSaveState.Idle
    }
}
