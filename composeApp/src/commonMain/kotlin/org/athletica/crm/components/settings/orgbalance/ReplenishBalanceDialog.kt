package org.athletica.crm.components.settings.orgbalance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_pay
import org.athletica.crm.generated.resources.action_replenish_balance
import org.athletica.crm.generated.resources.label_amount
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог пополнения баланса организации.
 * Содержит единственное поле — сумму пополнения. Кнопка «Оплатить» активна,
 * пока введённая сумма распознаётся как положительное число.
 * По нажатию вызывает [onPay] с введённой суммой; диалог закрывается
 * через [onDismiss] (отмена или внешний клик).
 */
@Composable
fun ReplenishBalanceDialog(
    onPay: (amount: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val isValid = amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_replenish_balance)) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(Res.string.label_amount)) },
                suffix = { Text("₽") },
                singleLine = true,
                isError = amountText.isNotBlank() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { amount?.let(onPay) },
            ) {
                Text(stringResource(Res.string.action_pay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
