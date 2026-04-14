package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_apply
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_credit
import org.athletica.crm.generated.resources.action_debit
import org.athletica.crm.generated.resources.dialog_adjust_balance_title
import org.athletica.crm.generated.resources.label_amount
import org.athletica.crm.generated.resources.label_comment
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог административной корректировки баланса клиента.
 * Позволяет выбрать направление (Пополнить / Списать), ввести сумму и комментарий.
 * По завершении вызывает [onSuccess] с обновлёнными данными клиента.
 */
@Composable
fun AdjustBalanceDialog(
    api: ApiClient,
    clientId: ClientId,
    onSuccess: (ClientDetailResponse) -> Unit,
    onDismiss: () -> Unit,
) {
    var isCredit by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val amountValid = amountText.toDoubleOrNull()?.let { it > 0 } == true
    val canSubmit = amountValid && note.isNotBlank() && !isLoading

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(Res.string.dialog_adjust_balance_title)) },
        text = {
            Column {
                // Переключатель Пополнить / Списать
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isCredit,
                        onClick = {
                            isCredit = true
                            error = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !isLoading,
                        label = { Text(stringResource(Res.string.action_credit)) },
                    )
                    SegmentedButton(
                        selected = !isCredit,
                        onClick = {
                            isCredit = false
                            error = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !isLoading,
                        label = { Text(stringResource(Res.string.action_debit)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Поле суммы (только положительные числа)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.label_amount)) },
                    suffix = { Text("₽") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && !amountValid,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Поле комментария (обязательное)
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.label_comment)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Spacer(Modifier.height(6.dp))
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
                enabled = canSubmit,
                onClick = {
                    val amount = amountText.toDouble() * (if (isCredit) 1.0 else -1.0)
                    scope.launch {
                        isLoading = true
                        error = null
                        api.adjustClientBalance(AdjustBalanceRequest(clientId, amount, note))
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
                                ifRight = { updated ->
                                    isLoading = false
                                    onSuccess(updated)
                                },
                            )
                    }
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.action_apply))
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
