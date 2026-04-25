package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_employee
import org.athletica.crm.generated.resources.action_deactivate_selected
import org.athletica.crm.generated.resources.action_export_selected
import org.athletica.crm.generated.resources.action_notify_selected_employees
import org.athletica.crm.generated.resources.cd_send_access
import org.athletica.crm.generated.resources.employees_empty
import org.athletica.crm.generated.resources.label_selected_count
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка сотрудников организации.
 * Отображает таблицу с аватаром, именем и статусом.
 * Поддерживает множественный выбор с нижней панелью действий.
 * [refreshKey] — при изменении перезагружает список.
 * [onNavigateToCreate] — переход к экрану добавления сотрудника.
 */
@Composable
fun EmployeesScreen(
    api: ApiClient,
    onNavigateToCreate: () -> Unit = {},
    refreshKey: Int = 0,
    onEmployeeClick: (EmployeeId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { EmployeesViewModel(api, scope) }

    LaunchedEffect(refreshKey) { viewModel.load() }

    var selectedIds by remember { mutableStateOf<Set<EmployeeId>>(emptySet()) }
    var showSendAccessFor by remember { mutableStateOf<EmployeeListItem?>(null) }

    showSendAccessFor?.let { emp ->
        SendAccessDialog(
            api = api,
            employeeId = emp.id,
            defaultEmail = emp.email,
            onSuccess = {
                showSendAccessFor = null
                selectedIds = emptySet()
                viewModel.load()
            },
            onDismiss = { showSendAccessFor = null },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreate,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_employee)) },
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                val employees = (viewModel.state as? EmployeesState.Loaded)?.employees ?: emptyList()
                val sendAccessTarget: EmployeeListItem? =
                    if (selectedIds.size == 1) {
                        val id = selectedIds.first()
                        employees.find { it.id == id }?.takeIf { !it.isActive }
                    } else {
                        null
                    }
                EmployeesBottomActionBar(
                    selectedCount = selectedIds.size,
                    sendAccessEnabled = sendAccessTarget != null,
                    onSendAccess = { showSendAccessFor = sendAccessTarget },
                    onNotify = {},
                    onDeactivate = {},
                    onExport = {},
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = viewModel.state) {
                is EmployeesState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is EmployeesState.Error ->
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is EmployeesState.Loaded -> {
                    if (s.employees.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.employees_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        val selectAllState =
                            when {
                                selectedIds.isEmpty() -> ToggleableState.Off
                                selectedIds.containsAll(s.employees.map { it.id }) -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }

                        Column(Modifier.fillMaxSize()) {
                            EmployeesTableHeader(
                                selectAllState = selectAllState,
                                onSelectAllClick = {
                                    selectedIds =
                                        if (selectAllState == ToggleableState.On) {
                                            emptySet()
                                        } else {
                                            s.employees.map { it.id }.toSet()
                                        }
                                },
                            )
                            HorizontalDivider()
                            LazyColumn(
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(s.employees, key = { it.id }) { employee ->
                                    EmployeeRow(
                                        employee = employee,
                                        api = api,
                                        selected = employee.id in selectedIds,
                                        onCheckedChange = { checked ->
                                            selectedIds =
                                                if (checked) selectedIds + employee.id else selectedIds - employee.id
                                        },
                                        onClick = { onEmployeeClick(employee.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeesBottomActionBar(
    selectedCount: Int,
    sendAccessEnabled: Boolean,
    onSendAccess: () -> Unit,
    onNotify: () -> Unit,
    onDeactivate: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Text(
            text = stringResource(Res.string.label_selected_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
        IconButton(onClick = onSendAccess, enabled = sendAccessEnabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(Res.string.cd_send_access),
            )
        }
        IconButton(onClick = onNotify) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(Res.string.action_notify_selected_employees),
            )
        }
        IconButton(onClick = onDeactivate) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = stringResource(Res.string.action_deactivate_selected),
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(Res.string.action_export_selected),
            )
        }
    }
}
