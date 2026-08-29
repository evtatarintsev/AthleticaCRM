package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double

/** Списание средств с баланса организации. */
class DebitCommand : SuspendingCliktCommand(name = "debit") {
    private val di by requireObject<AdminDi>()

    private val org by argument("org", help = "UUID или название организации")

    private val amount by argument("amount", help = "Сумма списания в основных единицах валюты").double()

    private val description by option("--description", help = "Описание операции").required()

    override fun help(context: Context): String = "Списать средства с баланса организации"

    override suspend fun run() = adjustOrgBalance(di, org, -amount, description)
}
