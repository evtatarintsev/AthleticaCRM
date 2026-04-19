package org.athletica.crm.components.clients

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_clear
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.hint_date_format
import org.athletica.crm.generated.resources.label_birthday
import org.athletica.crm.generated.resources.label_gender
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.screen_client_edit
import org.athletica.crm.pickImageFile
import org.jetbrains.compose.resources.stringResource

/**
 * Экран редактирования существующего клиента.
 * По завершении вызывает [onSaved] с обновлёнными данными, по отмене — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    client: ClientDetailResponse,
    api: ApiClient,
    onBack: () -> Unit,
    onSaved: (ClientDetailResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(client.name) }
    var gender by remember { mutableStateOf(client.gender) }
    var birthday by remember { mutableStateOf<LocalDate?>(client.birthday) }
    var showDatePicker by remember { mutableStateOf(false) }
    var avatarId by remember { mutableStateOf<UploadId?>(client.avatarId) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val busy = isSaving || isUploadingAvatar

    LaunchedEffect(client.avatarId) {
        val id = client.avatarId ?: return@LaunchedEffect
        api.uploadInfo(id).fold(
            ifLeft = { /* не критично — аватар просто не отобразится */ },
            ifRight = { avatarUrl = it.url },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_client_edit)) },
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
                                isSaving = true
                                error = null
                                api
                                    .editClient(
                                        EditClientRequest(
                                            id = client.id,
                                            name = name,
                                            avatarId = avatarId,
                                            birthday = birthday,
                                            gender = gender,
                                        ),
                                    ).fold(
                                        ifLeft = { err ->
                                            error =
                                                when (err) {
                                                    is ApiClientError.Unauthenticated -> "Сессия истекла"
                                                    is ApiClientError.ValidationError -> err.message
                                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                                }
                                            isSaving = false
                                        },
                                        ifRight = { updated ->
                                            isSaving = false
                                            onSaved(updated)
                                        },
                                    )
                            }
                        },
                        enabled = name.isNotBlank() && !busy,
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

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.label_gender),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Gender.entries.forEachIndexed { index, g ->
                        SegmentedButton(
                            selected = gender == g,
                            onClick = { if (!busy) gender = g },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Gender.entries.size),
                            label = {
                                Text(
                                    stringResource(
                                        when (g) {
                                            Gender.MALE -> Res.string.filter_gender_male
                                            Gender.FEMALE -> Res.string.filter_gender_female
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = birthday?.toString() ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.label_birthday)) },
                    placeholder = { Text(stringResource(Res.string.hint_date_format)) },
                    singleLine = true,
                    readOnly = true,
                    enabled = !busy,
                    trailingIcon = {
                        if (birthday != null) {
                            TextButton(onClick = { birthday = null }) { Text(stringResource(Res.string.action_clear)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = !busy) { showDatePicker = true },
                )
            }

            if (showDatePicker) {
                val initialDays = birthday?.toEpochDays()
                val datePickerState =
                    rememberDatePickerState(
                        initialSelectedDateMillis = if (initialDays != null) initialDays * 86_400_000L else null,
                    )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                birthday = LocalDate.fromEpochDays((millis / 86_400_000L).toInt())
                            }
                            showDatePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

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
