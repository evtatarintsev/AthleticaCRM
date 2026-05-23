package org.athletica.crm.ui.list

/**
 * Иммутабельный снимок состояния страницы со списком.
 *
 * Хранит данные [data], текущий фильтр [filter], сортировку [sort],
 * поисковый запрос [searchQuery] и идентификатор активного сохранённого
 * вида [activeSavedViewId]. Все переходы — чистые функции (`with…`),
 * возвращающие новый снимок через `copy(...)`; связанные инварианты
 * (например, сброс [activeSavedViewId] при ручном изменении фильтра или
 * сортировки) зашиты в сами переходы.
 *
 * Параметр [T] — тип элемента списка. Параметр [F] — тип фильтра раздела.
 */
data class ListPageState<T : Any, F : Any>(
    val data: ListData<T>,
    val filter: F,
    val sort: SortState?,
    val searchQuery: String,
    val activeSavedViewId: SavedViewId?,
) {
    /** Возвращает копию с новым состоянием загрузки. */
    fun withData(newData: ListData<T>): ListPageState<T, F> = copy(data = newData)

    /** Возвращает копию с новым поисковым запросом. */
    fun withSearch(query: String): ListPageState<T, F> = copy(searchQuery = query)

    /**
     * Возвращает копию с новым фильтром. Сбрасывает [activeSavedViewId],
     * так как ручное изменение фильтра выводит за пределы любого сохранённого вида.
     */
    fun withFilter(newFilter: F): ListPageState<T, F> = copy(filter = newFilter, activeSavedViewId = null)

    /**
     * Возвращает копию с новой сортировкой. Сбрасывает [activeSavedViewId]
     * по той же причине, что и [withFilter].
     */
    fun withSort(newSort: SortState?): ListPageState<T, F> = copy(sort = newSort, activeSavedViewId = null)

    /**
     * Возвращает копию, применяющую системный сохранённый вид [view]:
     * устанавливает фильтр, сортировку и [activeSavedViewId] одним переходом.
     */
    fun withSavedView(view: SystemSavedView<F>): ListPageState<T, F> = copy(filter = view.filter, sort = view.sort, activeSavedViewId = view.id)
}
