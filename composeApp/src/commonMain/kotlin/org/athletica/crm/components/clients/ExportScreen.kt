package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_download
import org.athletica.crm.generated.resources.export_select_fields
import org.athletica.crm.generated.resources.export_title
import org.jetbrains.compose.resources.stringResource

/**
 * Экран экспорта клиентов с возможностью выбора полей для экспорта.
 * [selectedClientIds] — список идентификаторов выбранных клиентов для экспорта.
 * [onBack] — действие при возврате на предыдущий экран.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    api: ApiClient,
    selectedClientIds: List<ClientId>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            ExportViewModel(
                api = api,
                scope = scope,
                selectedClientIds = selectedClientIds,
                onExportComplete = { onBack() },
            )
        }

    LaunchedEffect(Unit) { viewModel.loadCustomFields() }

    var selectedFields by remember {
        mutableStateOf(ClientField.entries.map { it.apiKey }.toSet())
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.export_title)) },
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
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.export_select_fields),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            ClientField.entries.forEach { field ->
                ExportFieldRow(
                    label = stringResource(field.labelRes()),
                    checked = field.apiKey in selectedFields,
                    onCheckedChange = { checked ->
                        selectedFields = selectedFields.toggle(field.apiKey, checked)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            viewModel.availableCustomFields.forEach { custom ->
                ExportFieldRow(
                    label = custom.label,
                    checked = custom.fieldKey.value in selectedFields,
                    onCheckedChange = { checked ->
                        selectedFields = selectedFields.toggle(custom.fieldKey.value, checked)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            when (val s = viewModel.state) {
                is ExportState.Error -> {
                    Text(
                        text = "Ошибка экспорта: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                is ExportState.Success -> {
                    Text(
                        text = "Экспорт успешно завершён! Файл готов к скачиванию.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                else -> {}
            }

            Button(
                onClick = { viewModel.export(orderedFields(viewModel.availableCustomFields, selectedFields), "csv") },
                enabled = viewModel.state !is ExportState.Exporting && selectedFields.isNotEmpty(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
            ) {
                if (viewModel.state is ExportState.Exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(Res.string.action_download))
                }
            }
        }
    }
}

/** Строка с переключателем для выбора одного поля экспорта. */
@Composable
private fun ExportFieldRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Возвращает множество с добавленным/удалённым элементом в зависимости от [include]. */
private fun Set<String>.toggle(
    key: String,
    include: Boolean,
): Set<String> = if (include) this + key else this - key

/**
 * Строит упорядоченный список ключей полей для экспорта в фиксированном порядке:
 * сначала стандартные в порядке [ClientField.entries], затем кастомные в порядке их определений.
 */
private fun orderedFields(
    customFields: List<CustomFieldDefinition>,
    selected: Set<String>,
): List<String> {
    val ordered = ClientField.entries.map { it.apiKey } + customFields.map { it.fieldKey.value }
    return ordered.filter { it in selected }
}
