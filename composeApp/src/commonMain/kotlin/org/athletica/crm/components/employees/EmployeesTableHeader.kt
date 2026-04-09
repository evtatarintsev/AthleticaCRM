package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.label_employee_email
import org.athletica.crm.generated.resources.label_employee_phone
import org.athletica.crm.generated.resources.label_employee_status
import org.athletica.crm.generated.resources.label_person_name
import org.jetbrains.compose.resources.stringResource

internal val StatusColWidth = 88.dp
internal val PhoneColWidth = 140.dp
internal val EmailColWidth = 180.dp

/**
 * Заголовок таблицы сотрудников: чекбокс «выбрать все» и названия колонок.
 */
@Composable
fun EmployeesTableHeader(
    selectAllState: ToggleableState,
    onSelectAllClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
    ) {
        // Пространство под аватар
        Spacer(Modifier.width(50.dp))

        Text(
            text = stringResource(Res.string.label_person_name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = stringResource(Res.string.label_employee_phone),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(PhoneColWidth),
        )

        Text(
            text = stringResource(Res.string.label_employee_email),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(EmailColWidth),
        )

        Text(
            text = stringResource(Res.string.label_employee_status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(StatusColWidth),
        )

        TriStateCheckbox(
            state = selectAllState,
            onClick = onSelectAllClick,
        )
    }
}
