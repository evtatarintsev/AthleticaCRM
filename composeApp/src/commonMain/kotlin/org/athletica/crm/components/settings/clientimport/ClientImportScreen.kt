package org.athletica.crm.components.settings.clientimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.import.ImportTarget
import org.athletica.crm.api.schemas.clients.import.LeadSourceAction
import org.athletica.crm.components.clients.message
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.import_action_commit
import org.athletica.crm.generated.resources.import_action_pick_file
import org.athletica.crm.generated.resources.import_action_validate
import org.athletica.crm.generated.resources.import_date_format_hint
import org.athletica.crm.generated.resources.import_date_format_label
import org.athletica.crm.generated.resources.import_default_gender_label
import org.athletica.crm.generated.resources.import_done_imported
import org.athletica.crm.generated.resources.import_done_skipped
import org.athletica.crm.generated.resources.import_done_title
import org.athletica.crm.generated.resources.import_done_total
import org.athletica.crm.generated.resources.import_lead_source_create_new
import org.athletica.crm.generated.resources.import_lead_source_existing
import org.athletica.crm.generated.resources.import_lead_source_section
import org.athletica.crm.generated.resources.import_lead_source_skip
import org.athletica.crm.generated.resources.import_mapping_column_header
import org.athletica.crm.generated.resources.import_mapping_section
import org.athletica.crm.generated.resources.import_mapping_target_header
import org.athletica.crm.generated.resources.import_pick_file_subtitle
import org.athletica.crm.generated.resources.import_preview_section
import org.athletica.crm.generated.resources.import_preview_table_errors
import org.athletica.crm.generated.resources.import_preview_table_row
import org.athletica.crm.generated.resources.import_target_balance
import org.athletica.crm.generated.resources.import_target_birthday
import org.athletica.crm.generated.resources.import_target_custom_field
import org.athletica.crm.generated.resources.import_target_gender
import org.athletica.crm.generated.resources.import_target_lead_source
import org.athletica.crm.generated.resources.import_target_name
import org.athletica.crm.generated.resources.import_target_skip
import org.athletica.crm.generated.resources.import_title
import org.athletica.crm.generated.resources.screen_client_import
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Экран мастера импорта клиентов из CSV.
 * Один stateless composable, отображающий разные шаги в зависимости от состояния [viewModel].
 *
 * Шаги:
 * 1. Выбор файла (Upload) — кнопка вызывает системный picker.
 * 2. Маппинг (Mapping) — таблица соответствия колонок и под-таблицы значений.
 * 3. Предпросмотр (Preview) — список ошибок строк после dryRun.
 * 4. Итог (Done) — счётчики после фактического импорта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientImportScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ClientImportViewModel(api, scope) }
    val isWorking = viewModel.action is ClientImportAction.Working
    val error = (viewModel.action as? ClientImportAction.Error)?.error

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_client_import)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (viewModel.phase == ClientImportPhase.Upload) {
                                onBack()
                            } else {
                                viewModel.onBack()
                            }
                        },
                        enabled = !isWorking,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ImportPhaseHeader(stringResource(Res.string.import_title))
            when (viewModel.phase) {
                ClientImportPhase.Upload ->
                    UploadStep(
                        isWorking = isWorking,
                        onPickFile = viewModel::onPickFile,
                    )

                ClientImportPhase.Mapping ->
                    MappingStep(
                        viewModel = viewModel,
                        isWorking = isWorking,
                    )

                ClientImportPhase.Preview ->
                    PreviewStep(
                        viewModel = viewModel,
                        isWorking = isWorking,
                    )

                ClientImportPhase.Done -> DoneStep(viewModel = viewModel, onClose = onBack)
            }

            if (error != null) {
                ErrorBanner(message = error.message(), onDismiss = viewModel::onErrorDismissed)
            }
        }
    }
}

@Composable
private fun ImportPhaseHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UploadStep(isWorking: Boolean, onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.import_pick_file_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPickFile, enabled = !isWorking) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(Res.string.import_action_pick_file))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingStep(viewModel: ClientImportViewModel, isWorking: Boolean) {
    val preview = viewModel.parsePreview ?: return
    val form = viewModel.form

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.import_mapping_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.import_mapping_column_header),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(Res.string.import_mapping_target_header),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                HorizontalDivider()
                preview.columns.forEach { col ->
                    ColumnMappingRow(
                        column = col,
                        current = form.columnMapping[col] ?: ImportTarget.Skip,
                        customFieldKeys = viewModel.customFieldDefs.map { it.fieldKey.value to it.label },
                        onChange = { viewModel.onColumnTargetChanged(col, it) },
                        enabled = !isWorking,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.import_default_gender_label),
                style = MaterialTheme.typography.labelMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Gender.entries.forEachIndexed { index, g ->
                    SegmentedButton(
                        selected = form.defaultGender == g,
                        onClick = { if (!isWorking) viewModel.onDefaultGenderChanged(g) },
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

        val genderColumn = form.columnMapping.entries.firstOrNull { it.value == ImportTarget.Gender }?.key
        if (genderColumn != null) {
            val uniqueValues = preview.uniqueValuesPerColumn[genderColumn].orEmpty()
            if (uniqueValues.isNotEmpty()) {
                GenderMappingSection(
                    values = uniqueValues,
                    mapping = form.genderMapping,
                    onChange = viewModel::onGenderMappingChanged,
                    enabled = !isWorking,
                )
            }
        }

        val leadColumn = form.columnMapping.entries.firstOrNull { it.value == ImportTarget.LeadSource }?.key
        if (leadColumn != null) {
            val uniqueValues = preview.uniqueValuesPerColumn[leadColumn].orEmpty()
            if (uniqueValues.isNotEmpty()) {
                LeadSourceMappingSection(
                    values = uniqueValues,
                    mapping = form.leadSourceMapping,
                    existingSources = viewModel.leadSources.map { it.id to it.name },
                    onChange = viewModel::onLeadSourceMappingChanged,
                    enabled = !isWorking,
                )
            }
        }

        if (form.columnMapping.values.any { it == ImportTarget.Birthday }) {
            OutlinedTextField(
                value = form.dateFormat,
                onValueChange = viewModel::onDateFormatChanged,
                label = { Text(stringResource(Res.string.import_date_format_label)) },
                placeholder = { Text(stringResource(Res.string.import_date_format_hint)) },
                singleLine = true,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = viewModel::onValidate,
            enabled = !isWorking && form.columnMapping.values.any { it == ImportTarget.Name },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(Res.string.import_action_validate))
        }
    }
}

@Composable
private fun ColumnMappingRow(
    column: String,
    current: ImportTarget,
    customFieldKeys: List<Pair<String, String>>,
    onChange: (ImportTarget) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = column, modifier = Modifier.weight(1f))
        TargetDropdown(
            current = current,
            customFieldKeys = customFieldKeys,
            onChange = onChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDropdown(
    current: ImportTarget,
    customFieldKeys: List<Pair<String, String>>,
    onChange: (ImportTarget) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = targetLabel(current, customFieldKeys)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownItem(Res.string.import_target_skip) {
                onChange(ImportTarget.Skip)
                expanded = false
            }
            DropdownItem(Res.string.import_target_name) {
                onChange(ImportTarget.Name)
                expanded = false
            }
            DropdownItem(Res.string.import_target_birthday) {
                onChange(ImportTarget.Birthday)
                expanded = false
            }
            DropdownItem(Res.string.import_target_gender) {
                onChange(ImportTarget.Gender)
                expanded = false
            }
            DropdownItem(Res.string.import_target_lead_source) {
                onChange(ImportTarget.LeadSource)
                expanded = false
            }
            DropdownItem(Res.string.import_target_balance) {
                onChange(ImportTarget.Balance)
                expanded = false
            }
            customFieldKeys.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.import_target_custom_field, label)) },
                    onClick = {
                        org.athletica.crm.core.customfields.CustomFieldKey
                            .from(key)
                            .onRight {
                                onChange(ImportTarget.CustomField(it))
                                expanded = false
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun DropdownItem(label: StringResource, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
    )
}

@Composable
private fun targetLabel(target: ImportTarget, customFieldKeys: List<Pair<String, String>>): String =
    when (target) {
        ImportTarget.Skip -> stringResource(Res.string.import_target_skip)
        ImportTarget.Name -> stringResource(Res.string.import_target_name)
        ImportTarget.Birthday -> stringResource(Res.string.import_target_birthday)
        ImportTarget.Gender -> stringResource(Res.string.import_target_gender)
        ImportTarget.LeadSource -> stringResource(Res.string.import_target_lead_source)
        ImportTarget.Balance -> stringResource(Res.string.import_target_balance)
        is ImportTarget.CustomField -> {
            val name = customFieldKeys.firstOrNull { it.first == target.key.value }?.second ?: target.key.value
            stringResource(Res.string.import_target_custom_field, name)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderMappingSection(
    values: List<String>,
    mapping: Map<String, Gender>,
    onChange: (String, Gender) -> Unit,
    enabled: Boolean,
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.import_target_gender),
                style = MaterialTheme.typography.titleSmall,
            )
            values.forEach { csvValue ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(csvValue, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        Gender.entries.forEachIndexed { index, g ->
                            SegmentedButton(
                                selected = mapping[csvValue] == g,
                                onClick = { if (enabled) onChange(csvValue, g) },
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadSourceMappingSection(
    values: List<String>,
    mapping: Map<String, LeadSourceAction>,
    existingSources: List<Pair<LeadSourceId, String>>,
    onChange: (String, LeadSourceAction) -> Unit,
    enabled: Boolean,
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.import_lead_source_section),
                style = MaterialTheme.typography.titleSmall,
            )
            values.forEach { csvValue ->
                LeadSourceRow(
                    csvValue = csvValue,
                    current = mapping[csvValue] ?: LeadSourceAction.Skip,
                    existingSources = existingSources,
                    onChange = { onChange(csvValue, it) },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadSourceRow(
    csvValue: String,
    current: LeadSourceAction,
    existingSources: List<Pair<LeadSourceId, String>>,
    onChange: (LeadSourceAction) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val label =
        when (current) {
            is LeadSourceAction.UseExisting -> {
                val name = existingSources.firstOrNull { it.first == current.id }?.second ?: ""
                stringResource(Res.string.import_lead_source_existing, name)
            }
            is LeadSourceAction.CreateNew -> stringResource(Res.string.import_lead_source_create_new, current.name)
            LeadSourceAction.Skip -> stringResource(Res.string.import_lead_source_skip)
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(csvValue, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.import_lead_source_skip)) },
                    onClick = {
                        onChange(LeadSourceAction.Skip)
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.import_lead_source_create_new, csvValue)) },
                    onClick = {
                        onChange(LeadSourceAction.CreateNew(csvValue))
                        expanded = false
                    },
                )
                existingSources.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.import_lead_source_existing, name)) },
                        onClick = {
                            onChange(LeadSourceAction.UseExisting(id))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStep(viewModel: ClientImportViewModel, isWorking: Boolean) {
    val validation = viewModel.validation ?: return
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.import_preview_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SummaryRow(validation.totalRows, validation.imported, validation.skipped)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(validation.rows) { row ->
                val color =
                    if (row.errors.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                Column {
                    Text(
                        text = stringResource(Res.string.import_preview_table_row, row.rowNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                    if (row.errors.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.import_preview_table_errors, row.errors.joinToString("; ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::onBack,
                enabled = !isWorking,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.action_back)) }
            Button(
                onClick = viewModel::onCommit,
                enabled = !isWorking && validation.imported > 0,
                modifier = Modifier.weight(1f),
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.import_action_commit))
            }
        }
    }
}

@Composable
private fun DoneStep(viewModel: ClientImportViewModel, onClose: () -> Unit) {
    val result = viewModel.commitResult ?: return
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.import_done_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SummaryRow(result.totalRows, result.imported, result.skipped)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_cancel))
        }
    }
}

@Composable
private fun SummaryRow(total: Int, imported: Int, skipped: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.import_done_total, total))
        Text(stringResource(Res.string.import_done_imported, imported))
        Text(
            text = stringResource(Res.string.import_done_skipped, skipped),
            color = if (skipped > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}
