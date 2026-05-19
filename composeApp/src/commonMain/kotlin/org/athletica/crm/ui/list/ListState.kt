package org.athletica.crm.ui.list

import org.athletica.crm.api.client.ApiClientError

/** Состояние асинхронной загрузки списка. */
sealed class ListState<out T> {
    /** Данные загружаются. */
    data object Loading : ListState<Nothing>()

    /** Произошла ошибка загрузки. [error] — ошибка API-клиента. */
    data class Error(val error: ApiClientError) : ListState<Nothing>()

    /** Данные успешно загружены. [items] — полный список до применения фильтра. */
    data class Loaded<T>(val items: List<T>) : ListState<T>()
}
