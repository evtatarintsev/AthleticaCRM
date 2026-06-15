package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.GroupId

/**
 * Параметры запроса списка клиентов с серверной фильтрацией, сортировкой,
 * поиском и пагинацией. Все поля имеют значения по умолчанию, чтобы запрос
 * без параметров вернул первую страницу активных клиентов, отсортированных по имени.
 */
@Serializable
data class ClientListRequest(
    /** Поисковый запрос по имени клиента (подстрока, регистронезависимо). `null` — без поиска. */
    val name: String? = null,
    /** Возвращать архивных клиентов вместо активных. По умолчанию — только активные. */
    val archived: Boolean = false,
    /** Максимальное количество клиентов на странице. Сервер ограничивает значение сверху. */
    val limit: Int = 50,
    /** Смещение от начала отфильтрованной выборки (для пагинации). */
    val offset: Int = 0,
    /** Поле сортировки. */
    val sortField: ClientSortField = ClientSortField.NAME,
    /** Направление сортировки. */
    val sortDirection: SortDirectionSchema = SortDirectionSchema.Asc,
    /** Фильтр по полу. `null` — без фильтра по полу. */
    val gender: Gender? = null,
    /** Только клиенты с задолженностью (отрицательный баланс). */
    val hasDebt: Boolean = false,
    /** Только клиенты, не состоящие ни в одной активной группе. */
    val noGroup: Boolean = false,
    /** Только участники указанной группы. `null` — без фильтра по группе. */
    val groupId: GroupId? = null,
)
