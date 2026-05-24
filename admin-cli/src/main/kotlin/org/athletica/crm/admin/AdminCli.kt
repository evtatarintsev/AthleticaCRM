package org.athletica.crm.admin

import arrow.core.raise.either
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import kotlinx.coroutines.runBlocking
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import kotlin.math.pow
import kotlin.math.roundToLong

/** Главная команда admin-cli. */
class AdminCli : CliktCommand(name = "athletica") {
    private val findQueries by option("--find", help = "Поиск по названию организации или логину владельца").multiple()
    private val orgArg by option("--org", help = "UUID или название организации")
    private val creditAmount by option("--credit", help = "Сумма зачисления (в основных единицах валюты)").double()
    private val debitAmount by option("--debit", help = "Сумма списания (в основных единицах валюты)").double()
    private val description by option("--description", help = "Описание операции (обязательно для --credit/--debit)")

    override fun run() =
        runBlocking {
            val di = AdminDi.fromEnv()
            when {
                findQueries.isNotEmpty() -> runFind(di)
                orgArg != null -> runBalance(di)
                else -> echo(currentContext.command.getFormattedHelp())
            }
        }

    private suspend fun runFind(di: AdminDi) {
        findQueries.forEach { query ->
            val results = di.orgSearch.find(query)
            if (results.isEmpty()) {
                echo("Не найдено: $query")
            } else {
                echo("Результаты для \"$query\":")
                results.forEach { printOrgInfo(it) }
            }
        }
    }

    private suspend fun runBalance(di: AdminDi) {
        if (creditAmount != null && debitAmount != null) {
            echo("Ошибка: укажите только --credit или только --debit, не оба сразу", err = true)
            return
        }
        val rawAmount = creditAmount ?: debitAmount?.unaryMinus()
        if (rawAmount == null) {
            echo("Ошибка: укажите --credit или --debit", err = true)
            return
        }
        if (description.isNullOrBlank()) {
            echo("Ошибка: --description обязателен для корректировки баланса", err = true)
            return
        }

        val org = di.orgSearch.resolve(orgArg!!)
        if (org == null) {
            echo("Организация не найдена: ${orgArg!!}", err = true)
            return
        }

        echo("Организация: ${org.name} (${org.id})")

        val scale = 10.0.pow(org.currency.fractionDigits)
        val minorUnits = (rawAmount * scale).roundToLong()
        val amount = Money(minorUnits, org.currency)
        val ctx = adminContext(OrgId(org.id), org.currency)

        val result =
            either {
                di.database.transaction {
                    context(ctx, this) {
                        val balance = di.orgBalances.current()
                        balance.adjust(amount, description!!)
                    }
                }
            }

        result.fold(
            { error -> echo("Ошибка: ${error.message}", err = true) },
            { updated ->
                val sign = if (amount.isPositive) "+" else ""
                echo("Корректировка: $sign${amount.formatted}")
                echo("Новый баланс:  ${updated.totalAmount.formatted}")
            },
        )
    }
}
