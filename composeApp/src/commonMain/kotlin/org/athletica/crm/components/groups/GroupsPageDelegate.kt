package org.athletica.crm.components.groups

import arrow.core.Either
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.ui.list.ColumnId
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView

/**
 * Типизированный фильтр раздела «Группы».
 * [disciplineIds] — оставить только группы с хотя бы одной из выбранных дисциплин; пустое — без фильтра.
 * [employeeIds] — оставить только группы с хотя бы одним из выбранных тренеров; пустое — без фильтра.
 */
data class GroupsFilterState(
    val disciplineIds: Set<DisciplineId> = emptySet(),
    val employeeIds: Set<EmployeeId> = emptySet(),
) {
    /** Количество активных фильтров для бейджа кнопки «Фильтры». */
    val activeCount: Int =
        (if (disciplineIds.isNotEmpty()) 1 else 0) +
            (if (employeeIds.isNotEmpty()) 1 else 0)
}

/**
 * Делегат страницы списка групп для [org.athletica.crm.ui.list.ListPageViewModel].
 *
 * Полностью без собственного состояния и корутин: предоставляет загрузку через API,
 * сортировку и хук персиста сортировки в [displaySettingsVm].
 * Фильтрация и поиск выполняются на сервере, поэтому [matches] всегда возвращает true.
 */
class GroupsPageDelegate(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
) : ListPageDelegate<GroupListItem, GroupsFilterState> {
    override fun defaultFilter(): GroupsFilterState = GroupsFilterState()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.groups.sort?.let(SortState::fromSchema)

    override suspend fun fetch(
        filter: GroupsFilterState,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<GroupListItem>> =
        api.groups
            .list(
                GroupListRequest(
                    name = searchQuery.takeIf { it.isNotBlank() },
                    disciplineIds = filter.disciplineIds.toList(),
                    employeeIds = filter.employeeIds.toList(),
                ),
            ).map { response ->
                FetchResult(items = response.groups, total = response.total.toInt())
            }

    override fun matches(
        item: GroupListItem,
        query: String,
        filter: GroupsFilterState,
    ): Boolean = true

    override fun compare(
        a: GroupListItem,
        b: GroupListItem,
        sort: SortState,
    ): Int {
        val cmp =
            when (sort.columnId) {
                GroupsColumns.Name -> a.name.compareTo(b.name, ignoreCase = true)
                GroupsColumns.Coaches ->
                    (a.employees.firstOrNull()?.name ?: "")
                        .compareTo(b.employees.firstOrNull()?.name ?: "", ignoreCase = true)
                else -> 0
            }
        return if (sort.direction == SortDirection.Asc) cmp else -cmp
    }

    override fun onSortChanged(sort: SortState?) {
        val current = displaySettingsVm.displaySettings
        displaySettingsVm.update(
            current.copy(
                groups = current.groups.copy(sort = sort?.toSchema()),
            ),
        )
    }
}

/** Идентификаторы колонок таблицы групп. */
object GroupsColumns {
    /** Колонка названия группы — всегда видима, сортируемая. */
    val Name = ColumnId("name")

    /** Колонка расписания группы — не сортируемая. */
    val Schedule = ColumnId("schedule")

    /** Колонка тренеров группы — сортируется по имени первого тренера. */
    val Coaches = ColumnId("coaches")
}

/** Системные сохранённые виды раздела «Группы». */
object GroupsSavedViews {
    /** Все группы без дополнительных фильтров. */
    val ALL: SystemSavedView<GroupsFilterState> =
        SystemSavedView(
            id = SavedViewId.system("groups:all"),
            filter = GroupsFilterState(),
        )

    /** Полный список системных видов в порядке отображения. */
    val ALL_VIEWS: List<SystemSavedView<GroupsFilterState>> = listOf(ALL)
}
