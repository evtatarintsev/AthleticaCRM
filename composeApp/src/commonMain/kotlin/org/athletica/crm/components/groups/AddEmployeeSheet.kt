package org.athletica.crm.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.employees.message
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_group_employee
import org.athletica.crm.generated.resources.employees_empty
import org.athletica.crm.generated.resources.hint_search
import org.jetbrains.compose.resources.stringResource

/**
 * Шторка выбора преподавателя для добавления в группу.
 * [groupId] — идентификатор группы.
 * [existingEmployeeIds] — преподаватели, которые уже привязаны к группе.
 * [onDismiss] — вызывается при закрытии шторки.
 * [onEmployeeAdded] — вызывается после успешной привязки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEmployeeSheet(
    groupId: GroupId,
    existingEmployeeIds: Set<EmployeeId>,
    api: ApiClient,
    onDismiss: () -> Unit,
    onEmployeeAdded: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val viewModel = remember { AddEmployeeViewModel(api, groupId, existingEmployeeIds, scope, onEmployeeAdded) }

    var query by remember { mutableStateOf("") }

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
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.action_add_group_employee),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
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

            when (val s = viewModel.state) {
                is AddEmployeeState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is AddEmployeeState.Error -> {
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }

                is AddEmployeeState.Loaded -> {
                    val filtered =
                        remember(s.employees, query) {
                            s.employees.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
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
                                    modifier =
                                        Modifier.clickable(enabled = !s.isAdding) {
                                            viewModel.onEmployeeSelected(employee.id)
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
