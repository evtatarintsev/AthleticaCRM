package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientExportRequest
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.utils.downloadFile

/**
 * ViewModel экрана экспорта клиентов. Управляет состоянием экспорта, загружает
 * список доступных кастомных полей и инициирует скачивание файла.
 */
class ExportViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    @Suppress("unused") private val selectedClientIds: List<ClientId>,
    private val onExportComplete: () -> Unit,
) {
    var state: ExportState by mutableStateOf(ExportState.Idle)
        private set

    /** Доступные кастомные поля клиента — отображаются как дополнительные пункты выбора. */
    var availableCustomFields by mutableStateOf<List<CustomFieldDefinition>>(emptyList())
        private set

    /** Загружает список кастомных полей клиента. */
    fun loadCustomFields() {
        scope.launch {
            api.customFields.list("CLIENT").fold(
                ifLeft = { },
                ifRight = { availableCustomFields = it },
            )
        }
    }

    /**
     * Запускает экспорт клиентов с выбранными полями.
     * [fields] — упорядоченный список ключей полей для экспорта.
     * [format] — формат экспорта ("csv" или "xlsx").
     */
    fun export(
        fields: List<String>,
        format: String = "csv",
    ) {
        state = ExportState.Exporting

        scope.launch {
            api.clients.export(ClientExportRequest(fields), format).fold(
                ifLeft = { error ->
                    state = ExportState.Error(error.toString())
                },
                ifRight = { csvData ->
                    downloadFile("clients.$format", csvData)
                    state = ExportState.Success(csvData)
                    onExportComplete()
                },
            )
        }
    }
}

/** Состояния экспорта. */
sealed class ExportState {
    /** Экспорт ещё не начат. */
    data object Idle : ExportState()

    /** Экспорт в процессе. */
    data object Exporting : ExportState()

    /** Экспорт успешно завершён. [data] содержит CSV данные. */
    data class Success(val data: ByteArray) : ExportState()

    /** Ошибка экспорта. [message] содержит описание ошибки. */
    data class Error(val message: String) : ExportState()
}
