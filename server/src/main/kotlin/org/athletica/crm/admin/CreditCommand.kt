package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double

/** Зачисление средств на баланс организации. */
class CreditCommand : SuspendingCliktCommand(name = "credit") {
    private val di by requireObject<AdminDi>()

    private val org by argument("org", help = "UUID или название организации")

    private val amount by argument("amount", help = "Сумма зачисления в основных единицах валюты").double()

    private val description by option("--description", help = "Описание операции").required()

    override fun help(context: Context): String = "Зачислить средства на баланс организации"

    override suspend fun run() = adjustOrgBalance(di, org, amount, description)
}
