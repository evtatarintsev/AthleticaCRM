package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.Context

/** Группа административных операций над организациями. */
class OrgCommand : SuspendingNoOpCliktCommand(name = "org") {
    override fun help(context: Context): String = "Операции над организациями"
}
