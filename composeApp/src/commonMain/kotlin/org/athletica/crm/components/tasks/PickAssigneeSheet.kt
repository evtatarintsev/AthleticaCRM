package org.athletica.crm.components.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.bulk_action_unassign
import org.athletica.crm.generated.resources.employees_empty
import org.athletica.crm.generated.resources.hint_search
import org.athletica.crm.generated.resources.pick_assignee_title
import org.athletica.crm.generated.resources.tasks_load_error
import org.jetbrains.compose.resources.stringResource

/**
 * Шторка одиночного выбора исполнителя для массовых действий над задачами.
 * Отображает список активных сотрудников и кнопку «Снять назначение».
 *
 * [api] — клиент API для загрузки списка сотрудников.
 * [onDismiss] — вызывается при закрытии шторки без выбора.
 * [onPicked] — вызывается с выбранным [EmployeeListItem] или null (снять назначение).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickAssigneeSheet(
    api: ApiClient,
    onDismiss: () -> Unit,
    onPicked: (EmployeeListItem?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var employees by remember { mutableStateOf<List<EmployeeListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<EmployeeId?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        api.employees.list().fold(
            ifLeft = {
                hasError = true
                isLoading = false
            },
            ifRight = { response ->
                employees = response.employees.filter { it.isActive }
                isLoading = false
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.pick_assignee_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onPicked(employees.find { it.id == selectedId }) },
                    enabled = !isLoading && !hasError,
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            }

            OutlinedButton(
                onClick = { onPicked(null) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(stringResource(Res.string.bulk_action_unassign))
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(Res.string.hint_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            when {
                isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }

                hasError -> {
                    Text(
                        text = stringResource(Res.string.tasks_load_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }

                else -> {
                    val filtered =
                        remember(employees, query) {
                            employees.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                        }

                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.employees_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(filtered, key = { it.id }) { employee ->
                                ListItem(
                                    headlineContent = { Text(employee.name) },
                                    leadingContent = {
                                        Box(modifier = Modifier.size(40.dp)) {
                                            Avatar(employee.avatarId, employee.name, api)
                                        }
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = employee.id == selectedId,
                                            onClick = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            selectedId =
                                                if (employee.id == selectedId) {
                                                    null
                                                } else {
                                                    employee.id
                                                }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
