package org.athletica.crm.components.settings.orgbalance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_pay
import org.athletica.crm.generated.resources.action_replenish_balance
import org.athletica.crm.generated.resources.label_amount
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Диалог пополнения баланса организации.
 * Содержит единственное поле — сумму пополнения. Кнопка «Оплатить» активна,
 * пока введённая сумма распознаётся как положительное число и не выполняется запрос ([isLoading] = false).
 * По нажатию вызывает [onPay] со суммой в виде [Money] (валюта = [currency]);
 * диалог закрывается через [onDismiss] (отмена или внешний клик).
 */
@Composable
fun ReplenishBalanceDialog(
    currency: Currency,
    isLoading: Boolean,
    onPay: (amount: Money) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val isValid = amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_replenish_balance)) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(Res.string.label_amount)) },
                suffix = { Text(currency.symbol) },
                singleLine = true,
                isError = amountText.isNotBlank() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid && !isLoading,
                onClick = {
                    amount?.let { value ->
                        val scale = pow10(currency.fractionDigits)
                        val minorUnits = round(value * scale).toLong()
                        onPay(Money(minorUnits, currency))
                    }
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = androidx.compose.ui.Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(stringResource(Res.string.action_pay))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

private fun pow10(digits: Int): Long {
    var result = 1L
    repeat(digits) { result *= 10 }
    return result
}
