package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.label_permissions
import org.athletica.crm.generated.resources.label_role_name
import org.athletica.crm.generated.resources.permission_can_view_client_balance_description
import org.athletica.crm.generated.resources.permission_can_view_client_balance_name
import org.athletica.crm.generated.resources.screen_role_create
import org.jetbrains.compose.resources.stringResource

/**
 * Экран создания или редактирования роли.
 *
 * [onBack] — возврат без сохранения.
 * [onSave] — вызывается с названием и набором выбранных прав.
 * [initialName] — начальное имя для режима редактирования.
 * [initialPermissions] — начальные права для режима редактирования.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleCreateScreen(
    onBack: () -> Unit,
    onSave: (name: String, permissions: Set<Permission>) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialPermissions: Set<Permission> = emptySet(),
) {
    val isEditMode = initialName.isNotEmpty()
    var name by remember { mutableStateOf(initialName) }
    var selectedPermissions by remember { mutableStateOf(initialPermissions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_role_create)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(name.trim(), selectedPermissions) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_role_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Text(
                text = stringResource(Res.string.label_permissions),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )

            Permission.entries.forEachIndexed { index, permission ->
                PermissionToggleItem(
                    permission = permission,
                    checked = permission in selectedPermissions,
                    onCheckedChange = { checked ->
                        selectedPermissions =
                            if (checked) {
                                selectedPermissions + permission
                            } else {
                                selectedPermissions - permission
                            }
                    },
                )
                if (index < Permission.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleItem(
    permission: Permission,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = permission.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = permission.displayDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun Permission.displayName(): String =
    when (this) {
        Permission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_name)
    }

@Composable
private fun Permission.displayDescription(): String =
    when (this) {
        Permission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_description)
    }
