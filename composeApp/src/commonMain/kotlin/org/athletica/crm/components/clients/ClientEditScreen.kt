package org.athletica.crm.components.clients

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.components.avatar.AvatarPicker
import org.athletica.crm.components.settings.DirectoryItem
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_clear
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.hint_date_format
import org.athletica.crm.generated.resources.hint_lead_source_select
import org.athletica.crm.generated.resources.label_birthday
import org.athletica.crm.generated.resources.label_gender
import org.athletica.crm.generated.resources.label_lead_source
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.screen_client_edit
import org.jetbrains.compose.resources.stringResource

/**
 * Экран редактирования существующего клиента.
 * По завершении вызывает [onSave] с данными формы, по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    client: ClientDetailResponse,
    saveState: ClientSaveState,
    onSave: (ClientForm) -> Unit,
    onBack: () -> Unit,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    var form by remember(client.id) {
        mutableStateOf(
            ClientForm(
                name = client.name,
                gender = client.gender,
                birthday = client.birthday,
                avatarId = client.avatarId,
                leadSourceId = client.leadSourceId,
            ),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var leadSources by remember { mutableStateOf<List<DirectoryItem<LeadSourceId>>>(emptyList()) }
    var leadSourceExpanded by remember { mutableStateOf(false) }

    val isSaving = saveState is ClientSaveState.Saving
    val saveError = (saveState as? ClientSaveState.Error)?.error

    LaunchedEffect(Unit) {
        api.leadSourceList().onRight { response ->
            leadSources = response.leadSources.map { DirectoryItem(id = it.id, name = it.name) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_client_edit)) },
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

            AvatarPicker(
                form.avatarId,
                api,
                onAvatarChanged = { form = form.copy(avatarId = it) },
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = { Text(stringResource(Res.string.label_person_name)) },
                singleLine = true,
                isError = saveError != null,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.label_gender),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Gender.entries.forEachIndexed { index, g ->
                        SegmentedButton(
                            selected = form.gender == g,
                            onClick = { if (!isSaving) form = form.copy(gender = g) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Gender.entries.size),
                            label = {
                                Text(
                                    stringResource(
                                        when (g) {
                                            Gender.MALE -> Res.string.filter_gender_male
                                            Gender.FEMALE -> Res.string.filter_gender_female
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = leadSourceExpanded,
                onExpandedChange = { leadSourceExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value =
                        form.leadSourceId?.let { id ->
                            leadSources.find { it.id == id }?.name ?: ""
                        } ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.label_lead_source)) },
                    placeholder = { Text(stringResource(Res.string.hint_lead_source_select)) },
                    singleLine = true,
                    readOnly = true,
                    enabled = !isSaving,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leadSourceExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = leadSourceExpanded,
                    onDismissRequest = { leadSourceExpanded = false },
                ) {
                    leadSources.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.name) },
                            onClick = {
                                form = form.copy(leadSourceId = source.id)
                                leadSourceExpanded = false
                            },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.birthday?.toString() ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.label_birthday)) },
                    placeholder = { Text(stringResource(Res.string.hint_date_format)) },
                    singleLine = true,
                    readOnly = true,
                    enabled = !isSaving,
                    trailingIcon = {
                        if (form.birthday != null) {
                            TextButton(onClick = { form = form.copy(birthday = null) }) {
                                Text(stringResource(Res.string.action_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = !isSaving) { showDatePicker = true },
                )
            }

            if (showDatePicker) {
                val initialDays = form.birthday?.toEpochDays()
                val datePickerState =
                    rememberDatePickerState(
                        initialSelectedDateMillis = if (initialDays != null) initialDays * 86_400_000L else null,
                    )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                form = form.copy(birthday = LocalDate.fromEpochDays((millis / 86_400_000L).toInt()))
                            }
                            showDatePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
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

@Composable
internal fun ClientEditScreenLoader(
    clientId: ClientId,
    api: ApiClient,
    onBack: () -> Unit,
    onSaved: (ClientDetailResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ClientEditViewModel(api, clientId, scope) { onSaved(it) } }

    when (val loadState = viewModel.loadState) {
        is ClientEditLoadState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is ClientEditLoadState.Error ->
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

        is ClientEditLoadState.Loaded ->
            ClientEditScreen(
                client = loadState.client,
                saveState = viewModel.saveState,
                onSave = { viewModel.onSave(it) },
                onBack = onBack,
                api = api,
                modifier = modifier,
            )
    }
}
