package org.athletica.crm.components.avatar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.UploadId

/**
 * Аватар: картинка загрузки [uploadId] либо инициалы [name], если картинки нет
 * или её не удалось загрузить (например, истёк presigned URL).
 * Заполняет родительский контейнер, поэтому форму и размер задаёт вызывающий код.
 */
@Composable
fun Avatar(uploadId: UploadId?, name: String, api: ApiClient) {
    var avatarUrl by remember(uploadId) { mutableStateOf<String?>(null) }
    var isBroken by remember(uploadId) { mutableStateOf(false) }
    LaunchedEffect(uploadId) {
        uploadId?.let { id ->
            api.documents.info(id).onRight { avatarUrl = it.url }
        }
    }
    val url = avatarUrl
    if (url != null && !isBroken) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { isBroken = true },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        TextAvatar(name)
    }
}

/** Инициалы (до двух букв) из [name] — запасной вариант, когда картинки аватара нет. */
@Composable
fun TextAvatar(name: String) {
    val initials =
        name
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .uppercase()

    Text(
        text = initials,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
