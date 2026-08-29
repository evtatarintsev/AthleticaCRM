package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple

/** Поиск организаций по названию организации или логину её владельца. */
class FindCommand : SuspendingCliktCommand(name = "find") {
    private val di by requireObject<AdminDi>()

    private val queries by argument("query", help = "Поисковый запрос").multiple(required = true)

    override fun help(context: Context): String = "Найти организации по названию или логину владельца"

    override suspend fun run() {
        val orgSearch = di.orgSearch
        queries.forEach { query ->
            val results =
                try {
                    orgSearch.find(query)
                } catch (e: Exception) {
                    echo("Ошибка при поиске \"$query\": ${e.message}", err = true)
                    return@forEach
                }
            if (results.isEmpty()) {
                echo("Не найдено: $query")
            } else {
                echo("Результаты для \"$query\":")
                results.forEach { echo(it.describe()) }
            }
        }
    }
}
