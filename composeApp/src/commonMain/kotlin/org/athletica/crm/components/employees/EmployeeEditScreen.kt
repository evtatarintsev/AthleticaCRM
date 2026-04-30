package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.error_roles_load_failed
import org.athletica.crm.generated.resources.hint_email
import org.athletica.crm.generated.resources.hint_phone
import org.athletica.crm.generated.resources.label_email
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_phone
import org.athletica.crm.generated.resources.screen_employee_edit
import org.jetbrains.compose.resources.stringResource

/** Экран редактирования сотрудника. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmployeeEditScreenLoader(
    employeeId: EmployeeId,
    api: ApiClient,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { EmployeeEditViewModel(api, employeeId, scope) { onSaved() } }

    when (val loadState = viewModel.loadState) {
        is EmployeeEditLoadState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is EmployeeEditLoadState.Error ->
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = loadState.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is EmployeeEditLoadState.Loaded -> {
            val employee = loadState.employee
            var form by remember(employee.id) {
                mutableStateOf(
                    EmployeeForm(
                        name = employee.name,
                        phoneNo = employee.phoneNo ?: "",
                        email = employee.email ?: "",
                        selectedRoleIds = employee.roles.map { it.id }.toSet(),
                        grantedPermissions = employee.grantedPermissions,
                        revokedPermissions = employee.revokedPermissions,
                        allBranchesAccess = employee.allBranchesAccess,
                        selectedBranchIds = employee.branchIds.toSet(),
                    ),
                )
            }

            val isSaving = viewModel.saveState is EmployeeSaveState.Saving
            val saveError = (viewModel.saveState as? EmployeeSaveState.Error)?.error
            val busy = isSaving || viewModel.isUploadingAvatar

            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.screen_employee_edit)) },
                        navigationIcon = {
                            IconButton(onClick = onBack, enabled = !busy) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_back),
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { viewModel.onSave(form) },
                                enabled = form.isValid && !busy,
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                } else {
                                    Text(stringResource(Res.string.action_save))
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    EmployeeAvatarPicker(
                        avatarUrl = viewModel.avatarUrl,
                        isLoading = viewModel.isUploadingAvatar,
                        name = form.name,
                        onClick = { viewModel.onUploadAvatar() },
                    )

                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = { Text(stringResource(Res.string.label_person_name)) },
                        singleLine = true,
                        isError = saveError != null,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.phoneNo,
                        onValueChange = { form = form.copy(phoneNo = it) },
                        label = { Text(stringResource(Res.string.label_phone)) },
                        placeholder = { Text(stringResource(Res.string.hint_phone)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.email,
                        onValueChange = { form = form.copy(email = it) },
                        label = { Text(stringResource(Res.string.label_email)) },
                        placeholder = { Text(stringResource(Res.string.hint_email)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (loadState.roles.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.error_roles_load_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        EmployeeRolesAndPermissionsForm(
                            roles = loadState.roles,
                            form = form,
                            onFormChange = { form = it },
                            enabled = !busy,
                            branches = loadState.branches,
                        )
                    }

                    if (saveError != null) {
                        Text(
                            text = saveError.message(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
