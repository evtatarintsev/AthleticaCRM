package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.storage.Transaction

/**
 * Колонка сортировки списка клиентов на стороне сервера.
 * Доменный аналог поля сортировки из API-схемы: проекция не зависит от `api.schemas`.
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
 * Все значения уже нормализованы вызывающим слоем (например, [limit] ограничен сверху).
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
 * Плоская строка проекции списка клиентов: всё, что нужно слою routes для сборки
 * элемента ответа, собрано одним запросом и без доменных сущностей-агрегатов.
 */
data class ClientListRow(
    /** Идентификатор клиента. */
    val id: ClientId,
    /** Имя клиента. */
    val name: String,
    /** Идентификатор аватарки, либо `null`. */
    val avatarId: UploadId?,
    /** День рождения, либо `null`. */
    val birthday: LocalDate?,
    /** Пол клиента. */
    val gender: Gender,
    /** Текущий баланс личного счёта (отрицательный — задолженность). */
    val balance: Money,
    /** Значения кастомных полей. */
    val customFields: List<CustomFieldValue>,
    /** Активные группы клиента. */
    val groups: List<ClientGroup>,
    /** Контакты клиента. */
    val contacts: List<ClientContact>,
    /** Клиент в архиве. */
    val archived: Boolean,
)

/**
 * Страница проекции списка клиентов.
 * [total] — общее число клиентов, удовлетворяющих фильтру (без учёта пагинации).
 */
data class ClientListPage(
    /** Клиенты текущей страницы. */
    val rows: List<ClientListRow>,
    /** Общее число клиентов, удовлетворяющих фильтру. */
    val total: Int,
)

/**
 * Read-side проекция списка клиентов: соединяет агрегаты (клиент, баланс, группы, контакты)
 * в одном запросе с серверной фильтрацией, сортировкой и пагинацией. Это не репозиторий
 * агрегата `Client`, а кросс-агрегатная выборка для нужд UI.
 */
interface ClientListView {
    /** Возвращает страницу клиентов согласно [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun page(query: ClientListQuery): ClientListPage
}
