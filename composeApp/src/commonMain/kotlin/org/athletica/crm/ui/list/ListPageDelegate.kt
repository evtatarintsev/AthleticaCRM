package org.athletica.crm.ui.list

import arrow.core.Either
import org.athletica.crm.api.client.ApiClientError

/**
 * Результат загрузки страницы списка.
 * [items] — собственно элементы.
 * [total] — общее количество записей на сервере (для пагинированных
 * выдач может отличаться от размера [items]); по умолчанию равно размеру [items].
 */
data class FetchResult<T>(val items: List<T>, val total: Int = items.size)

/**
 * Делегат поведения для [ListPageViewModel].
 *
 * Определяет начальные значения, стратегию загрузки данных,
 * клиентскую фильтрацию и сортировку. [ListPageViewModel] координирует состояние;
 * реализация отвечает только за доменную логику конкретного раздела —
 * без собственных корутин и без изменяемого состояния.
 *
 * Параметр [T] — тип элемента списка (схема из API).
 * Параметр [F] — типизированный фильтр раздела (data class).
 */
interface ListPageDelegate<T : Any, F : Any> {
    /** Возвращает начальное (пустое) состояние фильтра раздела. */
    fun defaultFilter(): F

    /** Возвращает сортировку по умолчанию. `null` — без явной сортировки. */
    fun defaultSort(): SortState?

    /**
     * Загружает первую страницу данных из источника.
     * Принимает актуальные [filter] и [searchQuery] на момент вызова,
     * возвращает либо ошибку, либо [FetchResult] с элементами и общим количеством.
     */
    suspend fun fetch(
        filter: F,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<T>>

    /**
     * Загружает страницу с учётом серверной сортировки [sort] и смещения [offset].
     * По умолчанию делегирует [fetch], игнорируя сортировку и пагинацию — поведение
     * для разделов с клиентской сортировкой и полной загрузкой. Разделы с серверной
     * пагинацией переопределяют метод и отправляют [sort]/[offset] в запрос.
     */
    suspend fun fetchPage(
        filter: F,
        searchQuery: String,
        sort: SortState?,
        offset: Int,
    ): Either<ApiClientError, FetchResult<T>> = fetch(filter, searchQuery)

    /**
     * Проверяет, попадает ли [item] в выдачу при текущих [query] и [filter].
     * Используется для клиентской фильтрации и поиска. По умолчанию — всегда `true`
     * (фильтрация выполняется на сервере).
     */
    fun matches(
        item: T,
        query: String,
        filter: F,
    ): Boolean = true

    /**
     * Сравнивает элементы [a] и [b] для клиентской сортировки согласно [sort].
     * По умолчанию — `0` (порядок сервера сохраняется без локальной пересортировки).
     */
    fun compare(
        a: T,
        b: T,
        sort: SortState,
    ): Int = 0

    /**
     * Должна ли смена сортировки перезагружать данные с сервера.
     * `true` — сортировка выполняется на сервере и требует рефетча; `false`
     * (по умолчанию) — сортировка клиентская через [compare], без перезагрузки.
     */
    val sortTriggersReload: Boolean get() = false

    /**
     * Хук, вызываемый при смене сортировки пользователем — для персиста
     * (например, в `DisplaySettings`). По умолчанию — no-op.
     */
    fun onSortChanged(sort: SortState?) {
    }
}
