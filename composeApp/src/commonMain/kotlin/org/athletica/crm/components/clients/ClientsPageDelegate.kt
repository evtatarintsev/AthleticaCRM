package org.athletica.crm.components.clients

import arrow.core.Either
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.ui.list.ColumnId
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView

/**
 * Делегат страницы списка клиентов для [org.athletica.crm.ui.list.ListPageViewModel].
 *
 * Полностью без собственного состояния и корутин: предоставляет загрузку через API,
 * клиентскую фильтрацию/сортировку и хук сохранения сортировки в [displaySettingsVm].
 */
class ClientsPageDelegate(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
) : ListPageDelegate<ClientListItem, ClientFilterState> {
    override fun defaultFilter(): ClientFilterState = ClientFilterState()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.clients.sort?.let(SortState::fromSchema)

    override suspend fun fetch(
        filter: ClientFilterState,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<ClientListItem>> =
        api.clients
            .list(
                ClientListRequest(
                    name = searchQuery.takeIf { it.isNotBlank() },
                    archived = filter.showArchived,
                ),
            )
            .map { response ->
                FetchResult(items = response.clients, total = response.total.toInt())
            }

    /**
     * Клиентская фильтрация по полу, задолженности и отсутствию группы.
     * Поиск по имени выполняется на сервере, поэтому [query] здесь не проверяется.
     */
    override fun matches(
        item: ClientListItem,
        query: String,
        filter: ClientFilterState,
    ): Boolean {
        val matchesGender = filter.gender == GenderFilter.All || item.gender == filter.gender.value
        val matchesDebt = !filter.hasDebtOnly || item.balance.isNegative
        val matchesGroup = !filter.noGroupOnly || item.groups.isEmpty()
        return matchesGender && matchesDebt && matchesGroup
    }

    override fun compare(
        a: ClientListItem,
        b: ClientListItem,
        sort: SortState,
    ): Int {
        val cmp =
            when (sort.columnId) {
                ClientsColumns.Name -> a.name.compareTo(b.name, ignoreCase = true)
                ClientsColumns.Balance -> a.balance.minorUnits.compareTo(b.balance.minorUnits)
                ClientsColumns.Birthday -> compareValues(a.birthday, b.birthday)
                else -> 0
            }
        return if (sort.direction == SortDirection.Asc) cmp else -cmp
    }

    override fun onSortChanged(sort: SortState?) {
        val current = displaySettingsVm.displaySettings
        displaySettingsVm.update(
            current.copy(
                clients = current.clients.copy(sort = sort?.toSchema()),
            ),
        )
    }
}

/** Идентификаторы колонок таблицы клиентов. */
object ClientsColumns {
    /** Колонка имени клиента — всегда видима. */
    val Name = ColumnId("name")

    /** Колонка баланса клиента. */
    val Balance = ColumnId("balance")

    /** Колонка даты рождения клиента. */
    val Birthday = ColumnId("birthday")
}

/** Системные сохранённые виды раздела «Клиенты». */
object ClientsSavedViews {
    /** Все клиенты без дополнительных фильтров. */
    val ALL: SystemSavedView<ClientFilterState> =
        SystemSavedView(
            id = SavedViewId.system("clients:all"),
            filter = ClientFilterState(),
        )

    /** Клиенты с задолженностью (отрицательный баланс). */
    val IN_DEBT: SystemSavedView<ClientFilterState> =
        SystemSavedView(
            id = SavedViewId.system("clients:debt"),
            filter = ClientFilterState(hasDebtOnly = true),
        )

    /** Клиенты, не состоящие ни в одной группе. */
    val WITHOUT_GROUP: SystemSavedView<ClientFilterState> =
        SystemSavedView(
            id = SavedViewId.system("clients:no-group"),
            filter = ClientFilterState(noGroupOnly = true),
        )

    /** Архивные клиенты. */
    val ARCHIVED: SystemSavedView<ClientFilterState> =
        SystemSavedView(
            id = SavedViewId.system("clients:archived"),
            filter = ClientFilterState(showArchived = true),
        )

    /** Полный список системных видов в порядке отображения. */
    val ALL_VIEWS: List<SystemSavedView<ClientFilterState>> = listOf(ALL, IN_DEBT, WITHOUT_GROUP, ARCHIVED)
}
