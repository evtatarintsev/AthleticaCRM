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
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.error_passwords_dont_match
import org.athletica.crm.generated.resources.label_confirm_password
import org.athletica.crm.generated.resources.label_current_password
import org.athletica.crm.generated.resources.label_new_password
import org.athletica.crm.generated.resources.screen_change_password
import org.jetbrains.compose.resources.stringResource

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
    val scope = rememberCoroutineScope()
    val viewModel = remember { ChangePasswordViewModel(api, scope) { onBack() } }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val isSaving = viewModel.saveState is ChangePasswordSaveState.Saving
    val saveError = (viewModel.saveState as? ChangePasswordSaveState.Error)?.error
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
                title = { Text(stringResource(Res.string.screen_change_password)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onSave(oldPassword, newPassword) },
                        enabled = canSave,
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
                onValueChange = {
                    oldPassword = it
                    viewModel.onErrorDismissed()
                },
                label = { Text(stringResource(Res.string.label_current_password)) },
                singleLine = true,
                enabled = !isSaving,
                isError = saveError != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    viewModel.onErrorDismissed()
                },
                label = { Text(stringResource(Res.string.label_new_password)) },
                singleLine = true,
                enabled = !isSaving,
                isError = newPassword.isNotBlank() && !passwordsMatch,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    viewModel.onErrorDismissed()
                },
                label = { Text(stringResource(Res.string.label_confirm_password)) },
                singleLine = true,
                enabled = !isSaving,
                isError = confirmPassword.isNotBlank() && !passwordsMatch,
                supportingText =
                    if (confirmPassword.isNotBlank() && !passwordsMatch) {
                        { Text(stringResource(Res.string.error_passwords_dont_match)) }
                    } else {
                        null
                    },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (saveError != null) {
                Text(
                    text = saveError.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
