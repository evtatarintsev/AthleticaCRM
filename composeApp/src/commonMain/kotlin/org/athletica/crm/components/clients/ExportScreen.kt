package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_download
import org.athletica.crm.generated.resources.export_fields_balance
import org.athletica.crm.generated.resources.export_fields_birthday
import org.athletica.crm.generated.resources.export_fields_gender
import org.athletica.crm.generated.resources.export_fields_groups
import org.athletica.crm.generated.resources.export_fields_name
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

    // Поля для экспорта с состояниями выбора
    var exportFields by remember {
        mutableStateOf(
            setOf(
                ExportField.NAME,
                ExportField.BIRTHDAY,
                ExportField.GENDER,
                ExportField.GROUPS,
                ExportField.BALANCE,
            ),
        )
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
                text = "Выберите поля для экспорта:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Чекбоксы для выбора полей
            ExportField.entries.forEach { field ->
                ExportFieldRow(
                    field = field,
                    checked = field in exportFields,
                    onCheckedChange = { checked ->
                        exportFields =
                            if (checked) {
                                exportFields + field
                            } else {
                                exportFields - field
                            }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Отображение состояния экспорта
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

            // Кнопка экспорта
            Button(
                onClick = {
                    viewModel.export(exportFields, "csv")
                },
                enabled = viewModel.state !is ExportState.Exporting,
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

/**
 * Строка с чекбоксом для выбора поля экспорта.
 */
@Composable
private fun ExportFieldRow(
    field: ExportField,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = field.getDisplayName(),
            modifier =
                Modifier
                    .padding(start = 48.dp)
                    .align(Alignment.CenterStart),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Поля для экспорта клиентов.
 */
enum class ExportField {
    NAME,
    BIRTHDAY,
    GENDER,
    GROUPS,
    BALANCE,
}

@Composable
private fun ExportField.getDisplayName(): String =
    stringResource(
        when (this) {
            ExportField.NAME -> Res.string.export_fields_name
            ExportField.BIRTHDAY -> Res.string.export_fields_birthday
            ExportField.GENDER -> Res.string.export_fields_gender
            ExportField.GROUPS -> Res.string.export_fields_groups
            ExportField.BALANCE -> Res.string.export_fields_balance
        },
    )
