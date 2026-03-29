package org.athletica.crm.components.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clients.ClientListItem
import kotlin.math.abs

internal val GenderColWidth: Dp = 52.dp
internal val BirthYearColWidth: Dp = 68.dp
internal val DebtColWidth: Dp = 84.dp

// Ширина области аватара: Spacer(8dp) + Box(36dp) + Spacer(10dp)
internal val AvatarAreaWidth: Dp = 54.dp

/** Строка клиента в таблице с чекбоксом, аватаром и колонками данных. */
@Composable
fun ClientRow(
    client: ClientListItem,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // TODO: заменить на реальные данные из API
    val absHash = abs(client.name.hashCode())
    val gender = if (absHash % 2 == 0) "М" else "Ж"
    val birthYear = 1970 + absHash % 36
    val debt =
        when (absHash % 4) {
            0 -> "1 200 ₽"
            1 -> "500 ₽"
            else -> "—"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 16.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )

        Spacer(Modifier.width(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = client.name.first().uppercaseChar().toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = client.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = gender,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(GenderColWidth),
        )

        Text(
            text = birthYear.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(BirthYearColWidth),
        )

        Text(
            text = debt,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(DebtColWidth),
        )
    }
}
