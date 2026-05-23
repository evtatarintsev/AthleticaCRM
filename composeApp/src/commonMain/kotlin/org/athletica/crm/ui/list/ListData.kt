package org.athletica.crm.ui.list

import org.athletica.crm.api.client.ApiClientError

/** Состояние асинхронной загрузки данных списка. */
sealed class ListData<out T> {
    /** Данные загружаются. */
    data object Loading : ListData<Nothing>()

    /** Произошла ошибка загрузки. [error] — ошибка API-клиента. */
    data class Error(val error: ApiClientError) : ListData<Nothing>()

    /**
     * Данные успешно загружены.
     * [items] — полный список до применения клиентского фильтра и сортировки.
     * [total] — общее количество записей на сервере (для пагинированных
     * списков может отличаться от размера [items]); по умолчанию равно
     * размеру [items].
     */
    data class Loaded<T>(val items: List<T>, val total: Int = items.size) : ListData<T>()
}
