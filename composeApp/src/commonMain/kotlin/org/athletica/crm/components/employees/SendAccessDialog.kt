package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_send_access
import org.athletica.crm.generated.resources.dialog_send_access_title
import org.athletica.crm.generated.resources.label_password_for_employee
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Диалог отправки доступа сотруднику.
 * Запрашивает пароль, вызывает `/api/employees/send-access` и сигнализирует об успехе через [onSuccess].
 */
@Composable
fun SendAccessDialog(
    api: ApiClient,
    employeeId: Uuid,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(Res.string.dialog_send_access_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.label_password_for_employee)) },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = error != null,
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        api.sendEmployeeAccess(SendEmployeeAccessRequest(employeeId, password))
                            .fold(
                                ifLeft = { err ->
                                    error =
                                        when (err) {
                                            is ApiClientError.Unauthenticated -> "Сессия истекла"
                                            is ApiClientError.ValidationError -> err.message
                                            is ApiClientError.Unavailable -> "Сервис недоступен"
                                        }
                                    isLoading = false
                                },
                                ifRight = {
                                    isLoading = false
                                    onSuccess()
                                },
                            )
                    }
                },
                enabled = password.isNotBlank() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.action_send_access))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
