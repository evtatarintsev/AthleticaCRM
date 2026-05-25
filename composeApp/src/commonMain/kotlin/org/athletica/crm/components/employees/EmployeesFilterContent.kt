package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.employees_filter_only_active
import org.athletica.crm.generated.resources.employees_filter_section_status
import org.jetbrains.compose.resources.stringResource

/**
 * Содержимое панели фильтров сотрудников.
 * Используется как слот внутри [org.athletica.crm.ui.list.ListPageFilterPanel].
 * Контейнер (заголовок, Reset, Apply) предоставляется scaffold-ом.
 *
 * [draft] — черновик фильтра; изменяется без перезагрузки списка.
 * [onDraftChange] — обновить черновик.
 */
@Composable
fun EmployeesFilterContent(
    draft: EmployeesFilter,
    onDraftChange: (EmployeesFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.employees_filter_section_status),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.employees_filter_only_active),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = draft.onlyActive,
                onCheckedChange = { onDraftChange(draft.copy(onlyActive = it)) },
            )
        }
    }
}
