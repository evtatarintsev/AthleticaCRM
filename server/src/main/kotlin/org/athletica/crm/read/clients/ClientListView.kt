package org.athletica.crm.read.clients

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Колонка сортировки списка клиентов на стороне сервера.
 * Отделена от поля сортировки в запросе: порядок сортировки — решение проекции,
 * а не контракта API, и допустимые колонки ограничены тем, что умеет SQL.
 */
enum class ClientSortColumn {
    /** По имени клиента. */
    NAME,

    /** По балансу личного счёта. */
    BALANCE,

    /** По дате рождения. */
    BIRTHDAY,
}

/**
 * Параметры запроса страницы списка клиентов: фильтры, сортировка и пагинация.
 * Все значения уже нормализованы вызывающим слоем (например, [limit] ограничен сверху,
 * а пустой поисковый запрос приведён к `null`).
 */
data class ClientListQuery(
    /** Возвращать архивных клиентов вместо активных. */
    val archived: Boolean = false,
    /** Поиск по имени (подстрока, регистронезависимо). `null` — без поиска. */
    val search: String? = null,
    /** Фильтр по полу. `null` — без фильтра. */
    val gender: Gender? = null,
    /** Только клиенты с отрицательным балансом. */
    val hasDebt: Boolean = false,
    /** Только клиенты без активной группы. */
    val noGroup: Boolean = false,
    /** Только участники указанной группы. `null` — без фильтра. */
    val groupId: GroupId? = null,
    /** Фильтр по дню рождения (диапазон дат по месяцу/дню). `null` — без фильтра. */
    val birthday: DateRange? = null,
    /** Колонка сортировки. */
    val sortColumn: ClientSortColumn = ClientSortColumn.NAME,
    /** Сортировать по возрастанию (`true`) или убыванию (`false`). */
    val ascending: Boolean = true,
    /** Размер страницы. */
    val limit: Int = 50,
    /** Смещение от начала выборки. */
    val offset: Int = 0,
)

/**
 * Read-проекция списка клиентов: соединяет данные нескольких агрегатов
 * (клиент, баланс, группы, контакты) в одном запросе с серверной фильтрацией,
 * сортировкой и пагинацией. Это не репозиторий агрегата `Client`, а кросс-агрегатная
 * выборка под конкретный экран, поэтому она сразу собирает готовый ответ API.
 */
interface ClientListView {
    /** Возвращает страницу клиентов согласно [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun page(query: ClientListQuery): ClientListResponse
}
