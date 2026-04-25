package org.athletica.crm.components.groups

import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.groups.ScheduleSlot

/**
 * Форма создания группы.
 * [isValid] — true когда название заполнено.
 */
data class GroupForm(
    val name: String = "",
    val schedule: List<ScheduleSlot> = emptyList(),
    val selectedDisciplines: List<DisciplineDetailResponse> = emptyList(),
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
