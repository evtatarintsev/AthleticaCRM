package org.athletica.crm.components.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.components.avatar.AvatarPicker
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.screen_edit_profile
import org.jetbrains.compose.resources.stringResource

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
    var avatarId by remember { mutableStateOf<UploadId?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val busy = isSaving

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_edit_profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                error = null
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
                                            isSaving = false
                                        },
                                        ifRight = {
                                            isSaving = false
                                            onBack()
                                        },
                                    )
                            }
                        },
                        enabled = name.isNotBlank() && !isLoading && !busy,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(Res.string.action_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
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

            AvatarPicker(
                avatarId,
                api,
                onAvatarChanged = { avatarId = it },
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
