package org.athletica.crm.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_photo
import org.athletica.crm.generated.resources.action_change_photo
import org.athletica.crm.pickImageFile
import org.jetbrains.compose.resources.stringResource

@Composable
fun AvatarPicker(
    currentAvatarId: UploadId?,
    api: ApiClient,
    onAvatarChanged: (UploadId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val onPickImageClick: () -> Unit = {
        scope.launch {
            val file = pickImageFile() ?: return@launch
            isLoading = true
            error = null
            api.documents
                .upload(
                    bytes = file.first,
                    filename = file.second,
                    contentType = file.third,
                ).fold(
                    ifLeft = { err ->
                        error =
                            when (err) {
                                is ApiClientError.Unauthenticated -> "Сессия истекла"
                                is ApiClientError.ValidationError -> err.message
                                is ApiClientError.Unavailable -> "Сервис недоступен"
                            }
                    },
                    ifRight = { upload ->
                        onAvatarChanged(upload.id)
                    },
                )
            isLoading = false
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !isLoading, onClick = onPickImageClick),
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                currentAvatarId != null -> Avatar(currentAvatarId, "", api)
                else ->
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(Res.string.action_add_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
            }
        }

        val actionText =
            if (currentAvatarId != null) {
                stringResource(Res.string.action_change_photo)
            } else {
                stringResource(Res.string.action_add_photo)
            }
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
