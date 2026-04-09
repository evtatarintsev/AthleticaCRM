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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.core.Gender
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.gender_female_abbr
import org.athletica.crm.generated.resources.gender_male_abbr
import org.jetbrains.compose.resources.stringResource

internal val GenderColWidth: Dp = 52.dp
internal val BirthYearColWidth: Dp = 68.dp
internal val DebtColWidth: Dp = 84.dp

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
    val data = client.fakeData()
    val genderLabel =
        when (client.gender) {
            Gender.MALE -> stringResource(Res.string.gender_male_abbr)
            Gender.FEMALE -> stringResource(Res.string.gender_female_abbr)
        }

    // Запрашиваем URL аватара лениво — только когда строка скомпозирована (видима в LazyColumn).
    // HttpCache на клиенте (Cache-Control: max-age=604800) исключает повторные запросы.
    var avatarUrl by remember(client.avatarId) { mutableStateOf<String?>(null) }
    LaunchedEffect(client.avatarId) {
        val id = client.avatarId
        if (id != null) {
            api.uploadInfo(id).onRight { avatarUrl = it.url }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // Аватар — всегда leading
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // avatarId как ключ кеша — URL обновляется каждые 7 дней, изображение нет
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = client.name.first().uppercaseChar().toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Имя растягивается
        Text(
            text = client.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        // Опциональные колонки
        if (ClientColumn.Gender in settings.visibleColumns) {
            Text(
                text = genderLabel,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(GenderColWidth),
            )
        }

        if (ClientColumn.BirthYear in settings.visibleColumns) {
            Text(
                text = data.birthYear.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(BirthYearColWidth),
            )
        }

        if (ClientColumn.Debt in settings.visibleColumns) {
            Text(
                text = data.debtLabel,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(DebtColWidth),
            )
        }

        // Чекбокс — trailing, изолирован от клика по строке
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )
    }
}
