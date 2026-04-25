package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_send_access
import org.athletica.crm.generated.resources.dialog_send_access_title
import org.athletica.crm.generated.resources.label_email_for_employee
import org.athletica.crm.generated.resources.label_password_for_employee
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог отправки доступа сотруднику.
 * Запрашивает email и пароль; email предзаполняется из профиля сотрудника ([defaultEmail]).
 * Вызывает [onSuccess] при успешной отправке.
 */
@Composable
fun SendAccessDialog(
    api: ApiClient,
    employeeId: EmployeeId,
    defaultEmail: String? = null,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { SendAccessViewModel(api, employeeId, scope) { onSuccess() } }

    var email by remember { mutableStateOf(defaultEmail ?: "") }
    var password by remember { mutableStateOf("") }

    val isSubmitting = viewModel.state is SendAccessState.Submitting
    val submitError = (viewModel.state as? SendAccessState.Error)?.error

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.dialog_send_access_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        viewModel.onErrorDismissed()
                    },
                    label = { Text(stringResource(Res.string.label_email_for_employee)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    isError = submitError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        viewModel.onErrorDismissed()
                    },
                    label = { Text(stringResource(Res.string.label_password_for_employee)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    isError = submitError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (submitError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = submitError.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.onSend(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank() && !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.action_send_access))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
