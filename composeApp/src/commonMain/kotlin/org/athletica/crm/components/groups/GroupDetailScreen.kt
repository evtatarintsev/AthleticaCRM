package org.athletica.crm.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupEmployee
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_group_employee
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_remove
import org.athletica.crm.generated.resources.dialog_remove_employee_from_group_message
import org.athletica.crm.generated.resources.dialog_remove_employee_from_group_title
import org.athletica.crm.generated.resources.employees_empty_for_group
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.section_basic_info
import org.athletica.crm.generated.resources.section_disciplines
import org.athletica.crm.generated.resources.section_group_employees
import org.jetbrains.compose.resources.stringResource

/**
 * Экран детальной информации о группе.
 * Отображает основную информацию, расписание и список преподавателей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { GroupDetailViewModel(api, groupId, scope) }

    var showAddEmployeeSheet by remember { mutableStateOf(false) }
    var employeeToRemove by remember { mutableStateOf<GroupEmployee?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val name = (viewModel.state as? GroupDetailState.Loaded)?.group?.name ?: ""
                    Text(name)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val s = viewModel.state) {
            is GroupDetailState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }
            }

            is GroupDetailState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                ) {
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is GroupDetailState.Loaded -> {
                val group = s.group
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    item {
                        GroupBasicInfoSection(group)
                    }

                    item {
                        GroupEmployeesSection(
                            employees = group.employees,
                            onAddEmployee = { showAddEmployeeSheet = true },
                            onRemoveEmployee = { employeeToRemove = it },
                        )
                    }
                }
            }
        }
    }

    if (showAddEmployeeSheet && viewModel.state is GroupDetailState.Loaded) {
        val group = (viewModel.state as GroupDetailState.Loaded).group
        AddEmployeeSheet(
            groupId = groupId,
            existingEmployeeIds = group.employees.map { it.id }.toSet(),
            api = api,
            onDismiss = { showAddEmployeeSheet = false },
            onEmployeeAdded = {
                showAddEmployeeSheet = false
                viewModel.load()
            },
        )
    }

    employeeToRemove?.let { employee ->
        val groupName = (viewModel.state as? GroupDetailState.Loaded)?.group?.name ?: ""
        AlertDialog(
            onDismissRequest = { employeeToRemove = null },
            title = { Text(stringResource(Res.string.dialog_remove_employee_from_group_title)) },
            text = { Text(stringResource(Res.string.dialog_remove_employee_from_group_message, employee.name, groupName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemoveEmployee(employee.id)
                        employeeToRemove = null
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.action_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { employeeToRemove = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun GroupBasicInfoSection(group: GroupDetailResponse) {
    SectionCard(stringResource(Res.string.section_basic_info)) {
        InfoRow(stringResource(Res.string.label_name), group.name)
        if (group.disciplines.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(Res.string.section_disciplines),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.disciplines.forEach { discipline ->
                    AssistChip(
                        onClick = {},
                        label = { Text(discipline.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupEmployeesSection(
    employees: List<GroupEmployee>,
    onAddEmployee: () -> Unit,
    onRemoveEmployee: (GroupEmployee) -> Unit,
) {
    SectionCard(stringResource(Res.string.section_group_employees)) {
        if (employees.isEmpty()) {
            Text(
                text = stringResource(Res.string.employees_empty_for_group),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                employees.forEach { employee ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(employee.name) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).padding(2.dp).clickable { onRemoveEmployee(employee) },
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AssistChip(
            onClick = onAddEmployee,
            label = { Text(stringResource(Res.string.action_add_group_employee)) },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}
