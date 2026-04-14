package org.athletica.crm.components.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.core.UploadId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_photo
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_change_photo
import org.athletica.crm.generated.resources.action_create
import org.athletica.crm.generated.resources.cd_avatar
import org.athletica.crm.generated.resources.hint_email
import org.athletica.crm.generated.resources.hint_phone
import org.athletica.crm.generated.resources.label_email
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_phone
import org.athletica.crm.generated.resources.screen_employee_create
import org.athletica.crm.pickImageFile
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Экран создания нового сотрудника.
 * По завершении вызывает [onCreated], по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCreateScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var phoneNo by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf<UploadId?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val busy = isCreating || isUploadingAvatar

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_employee_create)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isCreating = true
                                error = null
                                api
                                    .createEmployee(
                                        CreateEmployeeRequest(
                                            id = Uuid.generateV7(),
                                            name = name.trim(),
                                            phoneNo = phoneNo.trim().ifBlank { null },
                                            email = email.trim().ifBlank { null },
                                            avatarId = avatarId,
                                        ),
                                    ).fold(
                                        ifLeft = { err ->
                                            error =
                                                when (err) {
                                                    is ApiClientError.Unauthenticated -> "Сессия истекла"
                                                    is ApiClientError.ValidationError -> err.message
                                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                                }
                                            isCreating = false
                                        },
                                        ifRight = {
                                            isCreating = false
                                            onCreated()
                                        },
                                    )
                            }
                        },
                        enabled = name.isNotBlank() && email.isNotBlank() && !busy,
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(Res.string.action_create))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            EmployeeAvatarPicker(
                avatarUrl = avatarUrl,
                isLoading = isUploadingAvatar,
                name = name,
                onClick = {
                    scope.launch {
                        val file = pickImageFile() ?: return@launch
                        isUploadingAvatar = true
                        error = null
                        api
                            .uploadFile(
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
                                    avatarId = upload.id
                                    avatarUrl = upload.url
                                },
                            )
                        isUploadingAvatar = false
                    }
                },
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_person_name)) },
                singleLine = true,
                isError = error != null,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = phoneNo,
                onValueChange = { phoneNo = it },
                label = { Text(stringResource(Res.string.label_phone)) },
                placeholder = { Text(stringResource(Res.string.hint_phone)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.label_email)) },
                placeholder = { Text(stringResource(Res.string.hint_email)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmployeeAvatarPicker(
    avatarUrl: String?,
    isLoading: Boolean,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    .clickable(enabled = !isLoading, onClick = onClick),
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                avatarUrl != null ->
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = stringResource(Res.string.cd_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                name.isNotBlank() ->
                    Text(
                        text = name.first().uppercaseChar().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else ->
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(Res.string.action_add_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
            }
        }

        Text(
            text = if (avatarUrl != null) stringResource(Res.string.action_change_photo) else stringResource(Res.string.action_add_photo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
