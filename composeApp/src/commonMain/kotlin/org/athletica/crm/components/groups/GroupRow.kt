package org.athletica.crm.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.components.avatar.TextAvatar

/**
 * Строка группы в списке с чекбоксом и аватаром (первая буква названия).
 * Чекбокс изолирован от кликабельной области строки.
 */
@Composable
fun GroupRow(
    group: GroupListItem,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!selected) }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            TextAvatar(group.name)
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )
    }
}
