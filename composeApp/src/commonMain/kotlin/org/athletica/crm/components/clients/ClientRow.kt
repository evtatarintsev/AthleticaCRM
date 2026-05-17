package org.athletica.crm.components.clients

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.field
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.displayValue
import org.athletica.crm.core.money.formatted
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.gender_female_abbr
import org.athletica.crm.generated.resources.gender_male_abbr
import org.jetbrains.compose.resources.stringResource

// Ширина области аватара: Spacer(4dp) + Box(36dp) + Spacer(10dp)
internal val AvatarAreaWidth: Dp = 50.dp

/**
 * Строка клиента в таблице с чекбоксом, аватаром и колонками данных.
 * Чекбокс изолирован от кликабельной области — клик по нему только переключает выбор.
 * Клик по остальной части строки вызывает [onClick].
 * [settings] — управляет видимостью опциональных колонок.
 * Аватар запрашивается лениво через [api] только когда строка отображается на экране.
 */
@Composable
fun ClientRow(
    client: ClientListItem,
    api: ApiClient,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    settings: ClientDisplaySettings = ClientDisplaySettings(),
) {
    val genderLabel =
        when (client.gender) {
            Gender.MALE -> stringResource(Res.string.gender_male_abbr)
            Gender.FEMALE -> stringResource(Res.string.gender_female_abbr)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Avatar(client.avatarId, client.name, api)
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = client.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        settings.columns.forEach { column ->
            when (column) {
                is ClientColumn.Standard ->
                    StandardCell(
                        field = column.clientField,
                        client = client,
                        genderLabel = genderLabel,
                        width = column.width,
                    )
                is ClientColumn.Custom -> {
                    val value = client.field(column.apiKey)
                    if (value is CustomFieldValue.Bool) {
                        BoolCell(value = value.value, width = column.width)
                    } else {
                        Text(
                            text = value?.displayValue() ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(column.width),
                        )
                    }
                }
            }
        }

        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Рендерит ячейку стандартного поля [field] клиента. */
@Composable
private fun StandardCell(
    field: ClientField,
    client: ClientListItem,
    genderLabel: String,
    width: Dp,
) {
    when (field) {
        ClientField.GENDER ->
            Text(
                text = genderLabel,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(width),
            )
        ClientField.BIRTHDAY ->
            Text(
                text = client.birthday?.toString() ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(width),
            )
        ClientField.BALANCE ->
            Text(
                text = client.balance.formatted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(width),
            )
        ClientField.GROUPS ->
            Text(
                text = client.groups.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width),
            )
    }
}

/** Ячейка булева кастомного поля: иконка-галочка для true, прочерк для false. */
@Composable
private fun BoolCell(
    value: Boolean,
    width: Dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width(width),
    ) {
        if (value) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
