package org.athletica.crm.components.employees

import arrow.core.Either
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.ui.list.ColumnId
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView

/**
 * Типизированный фильтр раздела «Сотрудники».
 * [onlyActive] — показывать только активных сотрудников.
 */
data class EmployeesFilter(
    val onlyActive: Boolean = false,
) {
    /** Количество активных фильтров для бейджа кнопки «Фильтры». */
    val activeCount: Int = if (onlyActive) 1 else 0
}

/**
 * Делегат страницы списка сотрудников для [org.athletica.crm.ui.list.ListPageViewModel].
 *
 * API сотрудников отдаёт список целиком без серверной фильтрации, поэтому поиск и
 * фильтрация выполняются на клиенте через [matches]; [fetch] всегда возвращает
 * полный список. Сортировка персистится в [displaySettingsVm].
 */
class EmployeesPageDelegate(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
) : ListPageDelegate<EmployeeListItem, EmployeesFilter> {
    override fun defaultFilter(): EmployeesFilter = EmployeesFilter()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.employees.sort?.let(SortState::fromSchema)

    override suspend fun fetch(
        filter: EmployeesFilter,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<EmployeeListItem>> =
        api.employees.list().map { response ->
            FetchResult(items = response.employees, total = response.total.toInt())
        }

    override fun matches(
        item: EmployeeListItem,
        query: String,
        filter: EmployeesFilter,
    ): Boolean {
        if (filter.onlyActive && !item.isActive) {
            return false
        }
        if (query.isBlank()) {
            return true
        }
        val q = query.trim()
        return item.name.contains(q, ignoreCase = true) ||
            (item.email?.contains(q, ignoreCase = true) == true) ||
            (item.phoneNo?.contains(q, ignoreCase = true) == true)
    }

    override fun compare(
        a: EmployeeListItem,
        b: EmployeeListItem,
        sort: SortState,
    ): Int {
        val cmp =
            when (sort.columnId) {
                EmployeesColumns.Name -> a.name.compareTo(b.name, ignoreCase = true)
                EmployeesColumns.Active -> a.isActive.compareTo(b.isActive)
                else -> 0
            }
        return if (sort.direction == SortDirection.Asc) cmp else -cmp
    }

    override fun onSortChanged(sort: SortState?) {
        val current = displaySettingsVm.displaySettings
        displaySettingsVm.update(
            current.copy(
                employees = current.employees.copy(sort = sort?.toSchema()),
            ),
        )
    }
}

/** Идентификаторы колонок таблицы сотрудников. */
object EmployeesColumns {
    val Name = ColumnId("name")
    val Active = ColumnId("active")
    val Roles = ColumnId("roles")
    val Contact = ColumnId("contact")
}

/** Системные сохранённые виды раздела «Сотрудники». */
object EmployeesSavedViews {
    val ALL: SystemSavedView<EmployeesFilter> =
        SystemSavedView(
            id = SavedViewId.system("employees:all"),
            filter = EmployeesFilter(),
        )

    val ACTIVE: SystemSavedView<EmployeesFilter> =
        SystemSavedView(
            id = SavedViewId.system("employees:active"),
            filter = EmployeesFilter(onlyActive = true),
        )

    /** Полный список системных видов в порядке отображения. */
    val ALL_VIEWS: List<SystemSavedView<EmployeesFilter>> = listOf(ALL, ACTIVE)
}
