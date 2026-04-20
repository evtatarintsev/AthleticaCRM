package org.athletica.crm.components.avatar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.UploadId

@Composable
fun Avatar(uploadId: UploadId?, name: String, api: ApiClient) {
    var avatarUrl by remember(uploadId) { mutableStateOf<String?>(null) }
    LaunchedEffect(uploadId) {
        uploadId?.let { id ->
            api.uploadInfo(id).onRight { avatarUrl = it.url }
        }
    }
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    } else {
        TextAvatar(name)
    }
}

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
