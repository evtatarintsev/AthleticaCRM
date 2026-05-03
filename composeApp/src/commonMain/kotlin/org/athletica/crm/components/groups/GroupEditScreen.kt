package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_discipline
import org.athletica.crm.generated.resources.action_add_group_employee
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.screen_group_edit
import org.athletica.crm.generated.resources.section_disciplines
import org.athletica.crm.generated.resources.section_group_employees
import org.athletica.crm.generated.resources.section_schedule
import org.jetbrains.compose.resources.stringResource

/**
 * Экран редактирования существующей группы.
 * Содержит форму с редактируемыми полями.
 * По завершении вызывает [onSaved], по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupEditScreen(
    group: GroupDetailResponse,
    saveState: GroupSaveState,
    onSave: (GroupForm) -> Unit,
    onBack: () -> Unit,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    var form by remember(group.id) {
        mutableStateOf(
            GroupForm(
                name = group.name,
                schedule = group.schedule,
                selectedDisciplines = group.disciplines.map { it },
                selectedEmployees = group.employees.map { it },
            ),
        )
    }
    var showDisciplineSheet by remember { mutableStateOf(false) }
    var showEmployeeSheet by remember { mutableStateOf(false) }
    var halls by remember { mutableStateOf<List<HallDetailResponse>>(emptyList()) }

    val isSaving = saveState is GroupSaveState.Saving
    val saveError = (saveState as? GroupSaveState.Error)?.error

    LaunchedEffect(Unit) {
        api.halls.list().fold(ifLeft = {}, ifRight = { halls = it.halls })
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_group_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(form) },
                        enabled = form.isValid && !isSaving,
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
                isError = saveError != null,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            if (saveError != null) {
                Text(
                    text = saveError.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Text(stringResource(Res.string.section_disciplines), style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                form.selectedDisciplines.forEach { discipline ->
                    InputChip(
                        selected = true,
                        onClick = { form = form.copy(selectedDisciplines = form.selectedDisciplines - discipline) },
                        label = { Text(discipline.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = { showDisciplineSheet = true },
                    label = { Text(stringResource(Res.string.action_add_discipline)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = !isSaving,
                )
            }

            HorizontalDivider()

            Text(stringResource(Res.string.section_group_employees), style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                form.selectedEmployees.forEach { employee ->
                    InputChip(
                        selected = true,
                        onClick = { form = form.copy(selectedEmployees = form.selectedEmployees - employee) },
                        label = { Text(employee.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = { showEmployeeSheet = true },
                    label = { Text(stringResource(Res.string.action_add_group_employee)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = !isSaving,
                )
            }

            HorizontalDivider()

            Text(stringResource(Res.string.section_schedule), style = MaterialTheme.typography.titleMedium)

            ScheduleEditor(
                slots = form.schedule,
                halls = halls,
                onSlotsChange = { form = form.copy(schedule = it) },
            )
        }
    }

    if (showDisciplineSheet) {
        AddDisciplineSheet(
            existingDisciplineIds = form.selectedDisciplines.map { it.id }.toSet(),
            api = api,
            onDismiss = { showDisciplineSheet = false },
            onDisciplineSelected = { discipline ->
                form = form.copy(selectedDisciplines = form.selectedDisciplines + discipline)
                showDisciplineSheet = false
            },
        )
    }

    if (showEmployeeSheet) {
        PickEmployeesSheet(
            initialSelectedIds = form.selectedEmployees.map { it.id }.toSet(),
            api = api,
            onDismiss = { showEmployeeSheet = false },
            onEmployeesPicked = { employees ->
                form = form.copy(selectedEmployees = employees)
            },
        )
    }
}

@Composable
internal fun GroupEditScreenLoader(
    groupId: GroupId,
    api: ApiClient,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { GroupEditViewModel(api, groupId, scope) { onSaved() } }

    when (val loadState = viewModel.loadState) {
        is GroupEditLoadState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is GroupEditLoadState.Error ->
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

        is GroupEditLoadState.Loaded ->
            GroupEditScreen(
                group = loadState.group,
                saveState = viewModel.saveState,
                onSave = { viewModel.onSave(it) },
                onBack = onBack,
                api = api,
                modifier = modifier,
            )
    }
}
