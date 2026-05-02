package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.utils.downloadFile

/**
 * ViewModel для экрана экспорта клиентов.
 * Управляет состоянием экспорта и вызовами API.
 */
class ExportViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val selectedClientIds: List<ClientId>,
    private val onExportComplete: () -> Unit,
) {
    var state: ExportState by mutableStateOf(ExportState.Idle)
        private set

    /**
     * Запускает экспорт клиентов с выбранными полями.
     * [fields] — поля для экспорта (пока не используется, экспортируются все поля).
     * [format] — формат экспорта ("csv" или "xlsx").
     */
    fun export(fields: Set<ExportField>, format: String = "csv") {
        state = ExportState.Exporting

        scope.launch {
            // Создаём запрос на экспорт
            // Пока передаём пустой запрос, так как сервер экспортирует всех клиентов
            // В будущем можно добавить фильтрацию по ID
            val request = ClientListRequest()

            api.clients.export(request, format).fold(
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

/**
 * Состояния экспорта.
 */
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
