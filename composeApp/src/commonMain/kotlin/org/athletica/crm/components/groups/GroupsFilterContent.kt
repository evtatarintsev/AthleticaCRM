package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.filter_coaches
import org.athletica.crm.generated.resources.filter_disciplines
import org.jetbrains.compose.resources.stringResource

/**
 * Содержимое панели фильтров групп.
 * Используется как слот внутри [org.athletica.crm.ui.list.ListPageFilterPanel].
 * Контейнер (заголовок, Reset, Apply) предоставляется scaffold-ом.
 *
 * [draft] — черновик фильтра; изменяется без перезагрузки списка.
 * [onDraftChange] — обновить черновик.
 * [disciplines] — справочник дисциплин для мульти-выбора.
 * [employees] — справочник сотрудников для мульти-выбора.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsFilterContent(
    draft: GroupsFilterState,
    onDraftChange: (GroupsFilterState) -> Unit,
    disciplines: List<DisciplineDetailResponse>,
    employees: List<EmployeeListItem>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.filter_disciplines), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                disciplines.forEach { discipline ->
                    val selected = discipline.id in draft.disciplineIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newSet =
                                if (selected) {
                                    draft.disciplineIds - discipline.id
                                } else {
                                    draft.disciplineIds + discipline.id
                                }
                            onDraftChange(draft.copy(disciplineIds = newSet))
                        },
                        label = { Text(discipline.name) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.filter_coaches), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                employees.forEach { employee ->
                    val selected = employee.id in draft.employeeIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newSet =
                                if (selected) {
                                    draft.employeeIds - employee.id
                                } else {
                                    draft.employeeIds + employee.id
                                }
                            onDraftChange(draft.copy(employeeIds = newSet))
                        },
                        label = { Text(employee.name) },
                    )
                }
            }
        }
    }
}
