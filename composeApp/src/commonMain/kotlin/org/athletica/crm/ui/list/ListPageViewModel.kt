package org.athletica.crm.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel страницы со списком — координатор.
 *
 * Принимает специфичный для раздела [delegate] (источник данных,
 * клиентская фильтрация/сортировка, хук персиста). Сам по себе VM
 * не знает ни о конкретном API, ни о структуре фильтра — только о
 * контракте [ListPageDelegate].
 *
 * Состояние хранится в единственной изменяемой ячейке [state] —
 * иммутабельном снимке [ListPageState]. Все переходы идут через
 * чистые методы `with…` на снимке; экранные методы VM только
 * присваивают новый снимок. Производное [visible] пересчитывается
 * по [state] и автоматически инициирует рекомпозицию.
 *
 * Изменения [ListPageState.filter] и [ListPageState.searchQuery]
 * автоматически перезапускают [load] (поиск — с debounce 300 мс),
 * изменения [ListPageState.sort] — вызывают [ListPageDelegate.onSortChanged]
 * для персиста.
 *
 * Параметр [T] — тип элемента списка. Параметр [F] — тип фильтра раздела.
 */
@OptIn(FlowPreview::class)
class ListPageViewModel<T : Any, F : Any>(
    private val delegate: ListPageDelegate<T, F>,
    private val scope: CoroutineScope,
) {
    /** Текущий снимок состояния страницы. Единственная изменяемая ячейка. */
    var state: ListPageState<T, F> by mutableStateOf(
        ListPageState(
            data = ListData.Loading,
            filter = delegate.defaultFilter(),
            sort = delegate.defaultSort(),
            searchQuery = "",
            activeSavedViewId = null,
        ),
    )
        private set

    /**
     * Производный список после применения клиентской фильтрации, поиска и сортировки.
     * Compose отслеживает зависимость через [state].
     */
    val visible: List<T> get() = computeVisible()

    init {
        scope.launch {
            snapshotFlow { state.filter }
                .drop(1)
                .collectLatest { load() }
        }
        scope.launch {
            snapshotFlow { state.searchQuery }
                .drop(1)
                .debounce(300.milliseconds)
                .collectLatest { load() }
        }
        scope.launch {
            snapshotFlow { state.sort }
                .drop(1)
                .collectLatest { newSort -> delegate.onSortChanged(newSort) }
        }
    }

    /** Обновляет поисковый запрос. */
    fun setSearch(query: String) {
        state = state.withSearch(query)
    }

    /** Применяет новый фильтр (сбрасывает активный сохранённый вид). */
    fun setFilter(newFilter: F) {
        state = state.withFilter(newFilter)
    }

    /** Сбрасывает фильтр до значения по умолчанию. */
    fun resetFilter() {
        state = state.withFilter(delegate.defaultFilter())
    }

    /**
     * Циклически переключает сортировку для колонки [columnId].
     * Цикл: none → Asc → Desc → none.
     */
    fun cycleSort(columnId: ColumnId) {
        state = state.withSort(SortState.cycle(state.sort, columnId))
    }

    /** Напрямую устанавливает сортировку (например, из диалога). */
    fun applySort(newSort: SortState?) {
        state = state.withSort(newSort)
    }

    /** Применяет системный сохранённый вид: фильтр + сортировка + id вида. */
    fun applySystemView(view: SystemSavedView<F>) {
        state = state.withSavedView(view)
    }

    /** Перезагружает список из источника данных. */
    fun load() {
        scope.launch {
            state = state.withData(ListData.Loading)
            state =
                state.withData(
                    delegate.fetch(state.filter, state.searchQuery).fold(
                        ifLeft = { ListData.Error(it) },
                        ifRight = { ListData.Loaded(it.items, it.total) },
                    ),
                )
        }
    }

    private fun computeVisible(): List<T> {
        val loaded = state.data as? ListData.Loaded ?: return emptyList()
        val filtered = loaded.items.filter { delegate.matches(it, state.searchQuery, state.filter) }
        val sortDef = state.sort
        return if (sortDef != null) {
            filtered.sortedWith { a, b -> delegate.compare(a, b, sortDef) }
        } else {
            filtered
        }
    }
}
