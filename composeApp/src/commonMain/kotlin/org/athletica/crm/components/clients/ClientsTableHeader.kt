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

/**
 * Заголовок таблицы клиентов: чекбокс «выбрать все» и названия колонок.
 * [selectAllState] — состояние чекбокса (On / Off / Indeterminate).
 * [onSelectAllClick] — вызывается при нажатии на чекбокс.
 */
@Composable
fun ClientsTableHeader(
    selectAllState: ToggleableState,
    onSelectAllClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 16.dp),
    ) {
        TriStateCheckbox(
            state = selectAllState,
            onClick = onSelectAllClick,
        )

        Spacer(Modifier.width(AvatarAreaWidth))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Имя",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Сортировка по имени по возрастанию",
                modifier = Modifier.size(16.dp),
            )
        }

        Text(
            text = "Пол",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(GenderColWidth),
        )

        Text(
            text = "Год",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(BirthYearColWidth),
        )

        Text(
            text = "Долг",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(DebtColWidth),
        )
    }
}
