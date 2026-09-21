package org.athletica.crm.components.groups

import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem

/**
 * Форма создания группы. Расписание в неё не входит: у слота есть период действия,
 * поэтому расписание задаётся отдельной операцией с датой вступления в силу.
 * [isValid] — true когда название заполнено.
 */
data class GroupForm(
    val name: String = "",
    val selectedDisciplines: List<DisciplineDetailResponse> = emptyList(),
    val selectedEmployees: List<EmployeeListItem> = emptyList(),
) {
    val isValid: Boolean get() = name.isNotBlank()
}

/** Состояние сохранения группы. */
sealed class GroupSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : GroupSaveState()

    /** Сохранение выполняется. */
    data object Saving : GroupSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: GroupsApiError) : GroupSaveState()
}
