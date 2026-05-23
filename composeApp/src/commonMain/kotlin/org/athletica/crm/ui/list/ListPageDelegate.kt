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
 * Параметр [T] — тип элемента списка (DTO из API).
 * Параметр [F] — типизированный фильтр раздела (data class).
 */
interface ListPageDelegate<T : Any, F : Any> {
    /** Возвращает начальное (пустое) состояние фильтра раздела. */
    fun defaultFilter(): F

    /** Возвращает сортировку по умолчанию. `null` — без явной сортировки. */
    fun defaultSort(): SortState?

    /**
     * Загружает данные из источника.
     * Принимает актуальные [filter] и [searchQuery] на момент вызова,
     * возвращает либо ошибку, либо [FetchResult] с элементами и общим количеством.
     */
    suspend fun fetch(
        filter: F,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<T>>

    /**
     * Проверяет, попадает ли [item] в выдачу при текущих [query] и [filter].
     * Используется для клиентской фильтрации и поиска.
     */
    fun matches(
        item: T,
        query: String,
        filter: F,
    ): Boolean

    /**
     * Сравнивает элементы [a] и [b] для сортировки согласно [sort].
     * Возвращает отрицательное, нулевое или положительное значение.
     */
    fun compare(
        a: T,
        b: T,
        sort: SortState,
    ): Int

    /**
     * Хук, вызываемый при смене сортировки пользователем — для персиста
     * (например, в `DisplaySettings`). По умолчанию — no-op.
     */
    fun onSortChanged(sort: SortState?) {
    }
}
