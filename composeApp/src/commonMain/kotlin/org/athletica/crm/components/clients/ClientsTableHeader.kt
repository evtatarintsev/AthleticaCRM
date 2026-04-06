package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import org.athletica.crm.generated.resources.cd_sort_name_asc
import org.athletica.crm.generated.resources.label_debt_short
import org.athletica.crm.generated.resources.label_gender
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_year_short
import org.jetbrains.compose.resources.stringResource

/**
 * Заголовок таблицы клиентов: чекбокс «выбрать все» и названия колонок.
 * [selectAllState] — состояние чекбокса (On / Off / Indeterminate).
 * [onSelectAllClick] — вызывается при нажатии на чекбокс.
 * [settings] — настройки отображения, управляют видимостью опциональных колонок.
 */
@Composable
fun ClientsTableHeader(
    selectAllState: ToggleableState,
    onSelectAllClick: () -> Unit,
    settings: ClientDisplaySettings = ClientDisplaySettings(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
    ) {
        Spacer(Modifier.width(AvatarAreaWidth))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(Res.string.label_person_name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.cd_sort_name_asc),
                modifier = Modifier.size(16.dp),
            )
        }

        if (ClientColumn.Gender in settings.visibleColumns) {
            Text(
                text = stringResource(Res.string.label_gender),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(GenderColWidth),
            )
        }

        if (ClientColumn.BirthYear in settings.visibleColumns) {
            Text(
                text = stringResource(Res.string.label_year_short),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(BirthYearColWidth),
            )
        }

        if (ClientColumn.Debt in settings.visibleColumns) {
            Text(
                text = stringResource(Res.string.label_debt_short),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(DebtColWidth),
            )
        }

        // Trailing: совпадает с позицией чекбокса в строках ClientRow
        TriStateCheckbox(
            state = selectAllState,
            onClick = onSelectAllClick,
        )
    }
}
