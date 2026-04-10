package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_discipline
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_create
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.screen_group_create
import org.athletica.crm.generated.resources.section_disciplines
import org.athletica.crm.generated.resources.section_schedule
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Экран создания новой группы.
 * Содержит поле названия, редактор расписания и секцию привязки дисциплин.
 * По завершении вызывает [onCreated], по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupCreateScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf<List<ScheduleSlot>>(emptyList()) }
    var selectedDisciplines by remember { mutableStateOf<List<DisciplineDetailResponse>>(emptyList()) }
    var showDisciplineSheet by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_group_create)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isCreating) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isCreating = true
                                error = null
                                api
                                    .createGroup(
                                        GroupCreateRequest(
                                            id = Uuid.generateV7(),
                                            name = name,
                                            schedule = schedule,
                                            disciplineIds = selectedDisciplines.map { it.id },
                                        ),
                                    ).fold(
                                        ifLeft = { err ->
                                            error =
                                                when (err) {
                                                    is ApiClientError.Unauthenticated -> "Сессия истекла"
                                                    is ApiClientError.ValidationError -> err.message
                                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                                }
                                            isCreating = false
                                        },
                                        ifRight = {
                                            isCreating = false
                                            onCreated()
                                        },
                                    )
                            }
                        },
                        enabled = name.isNotBlank() && !isCreating,
                    ) {
                        if (isCreating) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
                isError = error != null,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Text(stringResource(Res.string.section_disciplines), style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedDisciplines.forEach { discipline ->
                    InputChip(
                        selected = true,
                        onClick = { selectedDisciplines = selectedDisciplines - discipline },
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
                    enabled = !isCreating,
                )
            }

            HorizontalDivider()

            Text(stringResource(Res.string.section_schedule), style = MaterialTheme.typography.titleMedium)

            ScheduleEditor(
                slots = schedule,
                onSlotsChange = { schedule = it },
            )
        }
    }

    if (showDisciplineSheet) {
        AddDisciplineSheet(
            existingDisciplineIds = selectedDisciplines.map { it.id }.toSet(),
            api = api,
            onDismiss = { showDisciplineSheet = false },
            onDisciplineSelected = { discipline ->
                selectedDisciplines = selectedDisciplines + discipline
                showDisciplineSheet = false
            },
        )
    }
}
