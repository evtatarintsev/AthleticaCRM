package org.athletica.crm.admin

import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.demo.DemoProfile
import org.athletica.crm.demo.ownerContext
import org.athletica.crm.domain.org.DbOrgCalendar
import kotlin.random.Random

/**
 * Наполнение существующей пустой организации историей работы за несколько месяцев.
 * Пользователей не создаёт: вход — учётной записью владельца, существовавшей до наполнения.
 */
class SeedCommand : SuspendingCliktCommand(name = "seed") {
    private val di by requireObject<AdminDi>()

    private val org by argument("org", help = "UUID или название организации")

    private val size by option("--size", help = "Профиль объёма: small, medium или large")
        .choice(DemoProfile.entries.associateBy { it.name.lowercase() })
        .default(DemoProfile.SMALL)

    private val seed by option("--seed", help = "Зерно генератора; одно зерно даёт одинаковые данные").int().default(1)

    override fun help(context: Context): String = "Наполнить пустую организацию демонстрационными данными"

    override suspend fun run() {
        val info = resolveOrg(di, org)
        echo("Организация: ${info.name} (${info.id})")
        val result =
            either {
                di.database.transaction {
                    context(ownerContext(OrgId(info.id), di.employees)) {
                        di.demoData.fill(size, Random(seed), DbOrgCalendar().today())
                    }
                }
            }
        result.fold(
            { error -> throw CliktError("Ошибка: ${error.message}") },
            { summary ->
                echo("Клиентов: ${summary.clients}, групп: ${summary.groups}, занятий: ${summary.sessions}, продаж: ${summary.sales}")
                echo("Списания по посещениям применит воркер событий запущенного сервера.")
            },
        )
    }
}
