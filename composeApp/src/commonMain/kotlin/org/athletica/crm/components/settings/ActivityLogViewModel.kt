package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.audit.AuditLogItem

private const val PAGE_SIZE = 50

/** Состояние экрана лога действий. */
sealed class ActivityLogState {
    /** Загрузка в процессе. */
    data object Loading : ActivityLogState()

    /** Данные загружены. */
    data class Loaded(val items: List<AuditLogItem>, val total: Long) : ActivityLogState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : ActivityLogState()
}

/**
 * ViewModel экрана лога действий.
 * Загружает записи аудита через [api] с пагинацией; [pageSize] строк на странице.
 */
class ActivityLogViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    /** Текущая страница (0-based). */
    var page by mutableIntStateOf(0)
        private set

    var state by mutableStateOf<ActivityLogState>(ActivityLogState.Loading)
        private set

    /** Размер страницы. */
    val pageSize: Int = PAGE_SIZE

    init {
        load()
    }

    /** Загружает текущую страницу. */
    fun load() {
        scope.launch {
            state = ActivityLogState.Loading
            api.audit.logList(page = page, pageSize = pageSize).fold(
                ifLeft = { state = ActivityLogState.Error(it.toSettingsApiError()) },
                ifRight = { state = ActivityLogState.Loaded(it.items, it.total) },
            )
        }
    }

    /** Переходит на следующую страницу. */
    fun nextPage() {
        val total = (state as? ActivityLogState.Loaded)?.total ?: return
        if ((page + 1) * pageSize < total) {
            page++
            load()
        }
    }

    /** Переходит на предыдущую страницу. */
    fun prevPage() {
        if (page > 0) {
            page--
            load()
        }
    }
}
