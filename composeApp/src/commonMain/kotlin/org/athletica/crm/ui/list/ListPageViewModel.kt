package org.athletica.crm.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClientError

/**
 * Базовая ViewModel для страницы со списком.
 *
 * Параметр [T] — тип элемента списка (DTO из API).
 * Параметр [F] — типизированный фильтр раздела (data class).
 *
 * Подкласс обязан реализовать [defaultFilter], [defaultSort], [fetch], [matches], [compare].
 * Остальная логика общая: загрузка, применение фильтра/поиска/сортировки, сохранённые виды.
 *
 * Производное состояние [visible] пересчитывается реактивно при изменении [state], [filter],
 * [searchQuery] и [sort].
 */
abstract class ListPageViewModel<T : Any, F : Any>(
    protected val scope: CoroutineScope,
) {
    /** Текущее состояние загрузки. */
    var state: ListState<T> by mutableStateOf(ListState.Loading)
        protected set

    /** Текущий фильтр раздела. */
    var filter: F by mutableStateOf(defaultFilter())
        private set

    /** Текущая сортировка. `null` — сортировка по умолчанию сервера. */
    var sort: SortState? by mutableStateOf(defaultSort())
        private set

    /** Текущий поисковый запрос. */
    var searchQuery: String by mutableStateOf("")
        private set

    /** Идентификатор активного сохранённого вида. `null` — ни один не выбран. */
    var activeSavedViewId: SavedViewId? by mutableStateOf(null)
        private set

    /**
     * Производный список после применения фильтра, поиска и сортировки.
     * Пересчитывается при каждом обращении; Compose отслеживает зависимости через [state],
     * [filter], [searchQuery] и [sort], автоматически инициируя рекомпозицию при изменении.
     */
    val visible: List<T>
        get() {
            val s = state
            if (s !is ListState.Loaded) {
                return emptyList()
            }
            val filtered = s.items.filter { matches(it, searchQuery, filter) }
            val sortDef = sort
            return if (sortDef != null) {
                filtered.sortedWith { a, b -> compare(a, b, sortDef) }
            } else {
                filtered
            }
        }

    /** Возвращает начальное (пустое) состояние фильтра раздела. */
    protected abstract fun defaultFilter(): F

    /** Возвращает сортировку по умолчанию. `null` — без явной сортировки. */
    protected abstract fun defaultSort(): SortState?

    /**
     * Загружает данные из API. Вызывается из [load].
     * Должен возвращать либо ошибку, либо полный список элементов.
     */
    protected abstract suspend fun fetch(): Either<ApiClientError, List<T>>

    /**
     * Проверяет, попадает ли [item] в выдачу при текущих [query] и [filter].
     * Используется для клиентской фильтрации и поиска.
     */
    protected abstract fun matches(
        item: T,
        query: String,
        filter: F,
    ): Boolean

    /**
     * Сравнивает элементы [a] и [b] для сортировки согласно [sort].
     * Возвращает отрицательное, нулевое или положительное значение.
     */
    protected abstract fun compare(
        a: T,
        b: T,
        sort: SortState,
    ): Int

    /** Перезагружает список из API. */
    fun load() {
        scope.launch {
            state = ListState.Loading
            state =
                fetch().fold(
                    ifLeft = { ListState.Error(it) },
                    ifRight = { ListState.Loaded(it) },
                )
        }
    }

    /** Обновляет поисковый запрос. */
    fun setSearch(query: String) {
        searchQuery = query
    }

    /**
     * Применяет новый фильтр. Сбрасывает [activeSavedViewId],
     * так как ручное изменение фильтра выводит за пределы любого сохранённого вида.
     */
    fun updateFilter(newFilter: F) {
        filter = newFilter
        activeSavedViewId = null
    }

    /** Сбрасывает фильтр до значения по умолчанию. */
    fun resetFilter() {
        filter = defaultFilter()
        activeSavedViewId = null
    }

    /**
     * Циклически переключает сортировку для колонки [columnId].
     * Цикл: none → Asc → Desc → none.
     */
    fun cycleSort(columnId: ColumnId) {
        sort = SortState.cycle(sort, columnId)
        activeSavedViewId = null
    }

    /**
     * Применяет системный сохранённый вид: устанавливает фильтр, сортировку и выбранный вид.
     */
    fun applySystemView(view: SystemSavedView<F>) {
        filter = view.filter
        sort = view.sort
        activeSavedViewId = view.id
    }
}
