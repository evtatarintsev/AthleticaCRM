package org.athletica.crm.admin

import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Корректирует баланс организации и печатает результат.
 * [di] — зависимости административных команд, [orgArg] — UUID или название организации,
 * [rawAmount] — величина корректировки в основных единицах валюты организации
 * (положительная — зачисление, отрицательная — списание), [description] — описание операции.
 */
internal suspend fun SuspendingCliktCommand.adjustOrgBalance(
    di: AdminDi,
    orgArg: String,
    rawAmount: Double,
    description: String,
) {
    val org =
        when (val resolution = di.orgSearch.resolve(orgArg)) {
            is OrgResolution.Found -> resolution.org
            is OrgResolution.Ambiguous ->
                throw CliktError(
                    (listOf("Найдено несколько организаций. Укажите UUID из списка:") + resolution.candidates.map { it.describe() })
                        .joinToString("\n"),
                )
            OrgResolution.NotFound -> throw CliktError("Организация не найдена: $orgArg")
        }

    echo("Организация: ${org.name} (${org.id})")

    val scale = 10.0.pow(org.currency.fractionDigits)
    val amount = Money((rawAmount * scale).roundToLong(), org.currency)
    val ctx = adminContext(OrgId(org.id), org.currency)

    val result =
        either {
            di.database.transaction {
                context(ctx) {
                    di.orgBalances.current().adjust(amount, description)
                }
            }
        }

    result.fold(
        { error -> throw CliktError("Ошибка: ${error.message}") },
        { updated ->
            val sign = if (amount.isPositive) "+" else ""
            echo("Корректировка: $sign${amount.formatted}")
            echo("Новый баланс:  ${updated.totalAmount.formatted}")
        },
    )
}
