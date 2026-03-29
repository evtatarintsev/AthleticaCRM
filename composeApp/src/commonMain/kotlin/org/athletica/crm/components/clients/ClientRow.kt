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
    val data = client.fakeData()

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
            text = data.gender,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(GenderColWidth),
        )

        Text(
            text = data.birthYear.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(BirthYearColWidth),
        )

        Text(
            text = data.debtLabel,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(DebtColWidth),
        )
    }
}
