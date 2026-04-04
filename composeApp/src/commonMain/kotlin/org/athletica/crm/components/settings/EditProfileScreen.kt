package org.athletica.crm.components.settings

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.athletica.crm.api.schemas.UpdateMeRequest
import kotlin.uuid.Uuid

/**
 * Экран «Редактировать профиль».
 * Загружает текущее имя и аватар через [api], позволяет их изменить и сохранить.
 *
 * [onBack] — переход назад (вызывается и после успешного сохранения).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf<Uuid?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        api.me().fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                        ApiClientError.Unauthenticated -> "Необходима авторизация"
                    }
            },
            ifRight = { profile ->
                name = profile.name
                avatarId = profile.avatarId
            },
        )
        isLoading = false
    }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            error = null
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Редактировать профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                api
                                    .updateMe(
                                        UpdateMeRequest(
                                            name = name.trim(),
                                            avatarId = avatarId,
                                        ),
                                    ).fold(
                                        ifLeft = { err ->
                                            error =
                                                when (err) {
                                                    is ApiClientError.ValidationError -> err.message
                                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                                    ApiClientError.Unauthenticated -> "Необходима авторизация"
                                                }
                                        },
                                        ifRight = { onBack() },
                                    )
                                isSaving = false
                            }
                        },
                        enabled = name.isNotBlank() && !isLoading && !isSaving,
                    ) {
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

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

            ProfileAvatarPicker(
                name = name,
                onClick = { /* TODO: загрузка файла через platform-specific file picker */ },
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Круглый аватар-заглушка: показывает инициал из [name], если он задан,
 * иначе — иконку камеры. Нажатие вызывает [onClick] для выбора файла.
 */
@Composable
private fun ProfileAvatarPicker(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasInitial = name.isNotBlank()

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
                    .background(
                        if (hasInitial) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(onClick = onClick),
        ) {
            if (hasInitial) {
                Text(
                    text = name.first().uppercaseChar().toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Выбрать фото",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Text(
            text = "Изменить фото",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
