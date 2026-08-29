package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.Context

/** Пространство административных команд: эксплуатационные операции над данными. */
class AdminCommand : SuspendingNoOpCliktCommand(name = "admin") {
    override fun help(context: Context): String = "Административные операции над данными"
}
