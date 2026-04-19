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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
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
    modifier: Modifier = Modifier,
) {
    var employees by remember { mutableStateOf<List<EmployeeListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<EmployeeId>>(emptySet()) }
    var showSendAccessFor by remember { mutableStateOf<EmployeeListItem?>(null) }
    var internalRefreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, internalRefreshKey) {
        isLoading = true
        api.employeeList().fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.Unauthenticated -> "Сессия истекла"
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                    }
            },
            ifRight = { response -> employees = response.employees },
        )
        isLoading = false
    }

    // Send-access dialog
    showSendAccessFor?.let { emp ->
        SendAccessDialog(
            api = api,
            employeeId = emp.id,
            defaultEmail = emp.email,
            onSuccess = {
                showSendAccessFor = null
                selectedIds = emptySet()
                internalRefreshKey++
            },
            onDismiss = { showSendAccessFor = null },
        )
    }

    // Determine if send-access is available: exactly 1 selected and that employee is inactive
    val sendAccessTarget: EmployeeListItem? =
        if (selectedIds.size == 1) {
            val id = selectedIds.first()
            val emp = employees.find { it.id == id }
            if (emp != null && !emp.isActive) emp else null
        } else {
            null
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
            when {
                isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                error != null ->
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )

                employees.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.employees_empty), style = MaterialTheme.typography.bodyLarge)
                    }

                else -> {
                    val selectAllState =
                        when {
                            selectedIds.isEmpty() -> ToggleableState.Off
                            selectedIds.containsAll(employees.map { it.id }) -> ToggleableState.On
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
                                        employees.map { it.id }.toSet()
                                    }
                            },
                        )
                        HorizontalDivider()
                        LazyColumn(
                            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(employees, key = { it.id }) { employee ->
                                EmployeeRow(
                                    employee = employee,
                                    api = api,
                                    selected = employee.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds =
                                            if (checked) selectedIds + employee.id else selectedIds - employee.id
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
