package org.athletica.crm.components.clients

import arrow.core.Either
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientSortField
import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.DateRange
import org.athletica.crm.ui.list.ColumnId
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView
import kotlin.time.Clock

/** Размер страницы списка клиентов при серверной пагинации. */
private const val CLIENTS_PAGE_SIZE = 50

/**
 * Делегат страницы списка клиентов для [org.athletica.crm.ui.list.ListPageViewModel].
 *
 * Без собственного состояния и корутин: фильтрация, сортировка, поиск и пагинация
 * выполняются на сервере (см. [fetchPage]); локальные [matches]/[compare] не нужны.
 * Смена сортировки требует рефетча ([sortTriggersReload] = true). Сохранение порядка —
 * хук [onSortChanged] в [displaySettingsVm].
 */
class ClientsPageDelegate(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
) : ListPageDelegate<ClientListItem, ClientFilterState> {
    override fun defaultFilter(): ClientFilterState = ClientFilterState()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.clients.sort?.let(SortState::fromSchema)

    override val sortTriggersReload: Boolean get() = true

    override suspend fun fetch(
        filter: ClientFilterState,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<ClientListItem>> = fetchPage(filter, searchQuery, defaultSort(), offset = 0)

    override suspend fun fetchPage(
        filter: ClientFilterState,
        searchQuery: String,
        sort: SortState?,
        offset: Int,
    ): Either<ApiClientError, FetchResult<ClientListItem>> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val birthdayRange: DateRange? =
            when (filter.birthdayFilter) {
                BirthdayFilter.None -> null
                BirthdayFilter.Today -> DateRange(today, today)
                BirthdayFilter.Tomorrow -> today.plus(1, DateTimeUnit.DAY).let { DateRange(it, it) }
                BirthdayFilter.ThisWeek -> DateRange(today, today.plus(6, DateTimeUnit.DAY))
            }
        return api.clients
            .list(
                ClientListRequest(
                    name = searchQuery.takeIf { it.isNotBlank() },
                    archived = filter.showArchived,
                    limit = CLIENTS_PAGE_SIZE,
                    offset = offset,
                    sortField = sort.toClientSortField(),
                    sortDirection = sort.toSortDirection(),
                    gender = filter.gender.value,
                    hasDebt = filter.hasDebtOnly,
                    noGroup = filter.noGroupOnly,
                    birthday = birthdayRange,
                ),
            )
            .map { response ->
                FetchResult(items = response.clients, total = response.total.toInt())
            }
    }

    /** Колонка сортировки UI → серверное поле сортировки (по умолчанию — имя). */
    private fun SortState?.toClientSortField(): ClientSortField =
        when (this?.columnId) {
            ClientsColumns.Balance -> ClientSortField.BALANCE
            ClientsColumns.Birthday -> ClientSortField.BIRTHDAY
            else -> ClientSortField.NAME
        }

    /** Направление сортировки UI → схема (по умолчанию — по возрастанию). */
    private fun SortState?.toSortDirection(): SortDirectionSchema = if (this?.direction == SortDirection.Desc) SortDirectionSchema.Desc else SortDirectionSchema.Asc

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
