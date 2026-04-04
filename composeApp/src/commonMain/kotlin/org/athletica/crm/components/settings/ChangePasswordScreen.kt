package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.ChangePasswordRequest

/**
 * Экран «Сменить пароль».
 * Форма: текущий пароль, новый пароль, подтверждение нового пароля.
 * Клиентская валидация: новый пароль == подтверждение, длина >= 6.
 * [onBack] — переход назад (вызывается и после успешной смены).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val passwordsMatch = newPassword == confirmPassword
    val canSave =
        oldPassword.isNotBlank() &&
            newPassword.isNotBlank() &&
            confirmPassword.isNotBlank() &&
            passwordsMatch &&
            !isSaving

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Сменить пароль") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                error = null
                                api.changePassword(
                                    ChangePasswordRequest(
                                        oldPassword = oldPassword,
                                        newPassword = newPassword,
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
                        enabled = canSave,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Сохранить")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("Текущий пароль") },
                singleLine = true,
                enabled = !isSaving,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Новый пароль") },
                singleLine = true,
                enabled = !isSaving,
                isError = newPassword.isNotBlank() && !passwordsMatch,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Подтверждение пароля") },
                singleLine = true,
                enabled = !isSaving,
                isError = confirmPassword.isNotBlank() && !passwordsMatch,
                supportingText =
                    if (confirmPassword.isNotBlank() && !passwordsMatch) {
                        { Text("Пароли не совпадают") }
                    } else {
                        null
                    },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
