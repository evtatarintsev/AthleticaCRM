package org.athletica.crm.components.groups

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.employees.EmployeesApiError
import org.athletica.crm.components.employees.message
import org.athletica.crm.components.employees.toEmployeesApiError
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_group_employee
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.employees_empty
import org.athletica.crm.generated.resources.hint_search
import org.jetbrains.compose.resources.stringResource

/**
 * Шторка выбора преподавателей (множественный выбор) для формы создания группы.
 * [initialSelectedIds] — уже выбранные преподаватели.
 * [onDismiss] — вызывается при закрытии шторки.
 * [onEmployeesPicked] — вызывается при нажатии "ОК" с полным списком выбранных объектов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickEmployeesSheet(
    initialSelectedIds: Set<EmployeeId>,
    api: ApiClient,
    onDismiss: () -> Unit,
    onEmployeesPicked: (List<EmployeeListItem>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var employees by remember { mutableStateOf<List<EmployeeListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<EmployeesApiError?>(null) }

    val selectedItems = remember { mutableStateListOf<EmployeeListItem>() }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        api.employees.list().fold(
            ifLeft = {
                error = it.toEmployeesApiError()
                isLoading = false
            },
            ifRight = { response ->
                employees = response.employees.filter { e -> e.isActive }
                selectedItems.clear()
                selectedItems.addAll(employees.filter { e -> e.id in initialSelectedIds })
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
                    text = stringResource(Res.string.action_add_group_employee),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onEmployeesPicked(selectedItems.toList())
                        onDismiss()
                    },
                    enabled = !isLoading && error == null,
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
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

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Text(
                    text = error!!.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                )
            } else {
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
                            val isSelected = selectedItems.any { it.id == employee.id }
                            ListItem(
                                headlineContent = { Text(employee.name) },
                                leadingContent = {
                                    Box(modifier = Modifier.size(40.dp)) {
                                        Avatar(employee.avatarId, employee.name, api)
                                    }
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null, // Handled by modifier
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        if (isSelected) {
                                            selectedItems.removeAll { it.id == employee.id }
                                        } else {
                                            selectedItems.add(employee)
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
