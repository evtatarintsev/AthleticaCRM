package org.athletica.crm.components.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.core.permissions.displayName
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_photo
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_change_photo
import org.athletica.crm.generated.resources.action_create
import org.athletica.crm.generated.resources.cd_avatar
import org.athletica.crm.generated.resources.error_roles_load_failed
import org.athletica.crm.generated.resources.hint_email
import org.athletica.crm.generated.resources.hint_phone
import org.athletica.crm.generated.resources.label_branch_access_all
import org.athletica.crm.generated.resources.label_email
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_phone
import org.athletica.crm.generated.resources.screen_employee_create
import org.athletica.crm.generated.resources.section_branch_access
import org.athletica.crm.generated.resources.section_granted_permissions
import org.athletica.crm.generated.resources.section_revoked_permissions
import org.athletica.crm.generated.resources.section_roles
import org.jetbrains.compose.resources.stringResource

/**
 * Экран создания нового сотрудника.
 * По завершении вызывает [onCreated], по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCreateScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { EmployeeCreateViewModel(api, scope) { onCreated() } }

    var form by remember { mutableStateOf(EmployeeForm()) }

    val isSaving = viewModel.saveState is EmployeeSaveState.Saving
    val saveError = (viewModel.saveState as? EmployeeSaveState.Error)?.error
    val busy = isSaving || viewModel.isUploadingAvatar

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_employee_create)) },
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
                        onClick = { viewModel.onCreate(form) },
                        enabled = form.isValid && !busy,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(Res.string.action_create))
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

            when (val ls = viewModel.loadState) {
                is EmployeeCreateLoadState.Loading ->
                    CircularProgressIndicator()

                is EmployeeCreateLoadState.Error ->
                    Text(
                        text = stringResource(Res.string.error_roles_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )

                is EmployeeCreateLoadState.Loaded ->
                    EmployeeRolesAndPermissionsForm(
                        roles = ls.roles,
                        form = form,
                        onFormChange = { form = it },
                        enabled = !busy,
                        branches = ls.branches,
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

/**
 * Секция выбора ролей и прав для формы сотрудника.
 * Используется на экранах создания и редактирования.
 */
@Composable
internal fun EmployeeRolesAndPermissionsForm(
    roles: List<RoleItem>,
    form: EmployeeForm,
    onFormChange: (EmployeeForm) -> Unit,
    enabled: Boolean,
    branches: List<org.athletica.crm.api.schemas.branches.BranchDetailResponse> = emptyList(),
) {
    if (roles.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.section_roles),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            roles.forEachIndexed { index, role ->
                ListItem(
                    headlineContent = { Text(role.name) },
                    trailingContent = {
                        Switch(
                            checked = role.id in form.selectedRoleIds,
                            onCheckedChange = { checked ->
                                onFormChange(
                                    form.copy(
                                        selectedRoleIds =
                                            if (checked) {
                                                form.selectedRoleIds + role.id
                                            } else {
                                                form.selectedRoleIds - role.id
                                            },
                                    ),
                                )
                            },
                            enabled = enabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index < roles.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    Text(
        text = stringResource(Res.string.section_granted_permissions),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Permission.entries.forEachIndexed { index, permission ->
            ListItem(
                headlineContent = { Text(permission.displayName()) },
                trailingContent = {
                    Switch(
                        checked = permission in form.grantedPermissions,
                        onCheckedChange = { checked ->
                            onFormChange(
                                if (checked) {
                                    form.copy(
                                        grantedPermissions = form.grantedPermissions + permission,
                                        revokedPermissions = form.revokedPermissions - permission,
                                    )
                                } else {
                                    form.copy(grantedPermissions = form.grantedPermissions - permission)
                                },
                            )
                        },
                        enabled = enabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (index < Permission.entries.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }

    Text(
        text = stringResource(Res.string.section_revoked_permissions),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Permission.entries.forEachIndexed { index, permission ->
            ListItem(
                headlineContent = { Text(permission.displayName()) },
                trailingContent = {
                    Switch(
                        checked = permission in form.revokedPermissions,
                        onCheckedChange = { checked ->
                            onFormChange(
                                if (checked) {
                                    form.copy(
                                        revokedPermissions = form.revokedPermissions + permission,
                                        grantedPermissions = form.grantedPermissions - permission,
                                    )
                                } else {
                                    form.copy(revokedPermissions = form.revokedPermissions - permission)
                                },
                            )
                        },
                        enabled = enabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (index < Permission.entries.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }

    if (branches.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.section_branch_access),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        ListItem(
            headlineContent = { Text(stringResource(Res.string.label_branch_access_all)) },
            trailingContent = {
                Switch(
                    checked = form.allBranchesAccess,
                    onCheckedChange = { checked ->
                        onFormChange(form.copy(allBranchesAccess = checked))
                    },
                    enabled = enabled,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (!form.allBranchesAccess) {
            Column(modifier = Modifier.fillMaxWidth()) {
                branches.forEachIndexed { index, branch ->
                    ListItem(
                        headlineContent = { Text(branch.name) },
                        trailingContent = {
                            Switch(
                                checked = branch.id in form.selectedBranchIds,
                                onCheckedChange = { checked ->
                                    onFormChange(
                                        form.copy(
                                            selectedBranchIds =
                                                if (checked) {
                                                    form.selectedBranchIds + branch.id
                                                } else {
                                                    form.selectedBranchIds - branch.id
                                                },
                                        ),
                                    )
                                },
                                enabled = enabled,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (index < branches.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmployeeAvatarPicker(
    avatarUrl: String?,
    isLoading: Boolean,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !isLoading, onClick = onClick),
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                avatarUrl != null ->
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = stringResource(Res.string.cd_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                name.isNotBlank() ->
                    Text(
                        text = name.first().uppercaseChar().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else ->
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(Res.string.action_add_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
            }
        }

        Text(
            text = if (avatarUrl != null) stringResource(Res.string.action_change_photo) else stringResource(Res.string.action_add_photo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
