package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import io.ktor.server.netty.EngineMain

/**
 * Корневая команда приложения: выбирает режим работы по подкоманде.
 * Регистрирует контейнер административных зависимостей в контексте — подключение к БД
 * открывается только тогда, когда к нему обращается административная команда.
 * [adminDi] — способ получить контейнер административных зависимостей.
 */
class AthleticaCommand(
    private val adminDi: () -> AdminDi,
) : SuspendingNoOpCliktCommand(name = "athletica") {
    override fun help(context: Context): String = "AthleticaCRM: веб-сервер и административные команды"

    override suspend fun run() {
        currentContext.findOrSetObject(defaultValue = adminDi)
    }
}

/**
 * Собирает дерево команд приложения.
 * [adminDi] — контейнер зависимостей административных команд,
 * [startServer] — запуск веб-сервера.
 */
fun athleticaCommand(
    adminDi: () -> AdminDi = { AdminDi.fromEnv() },
    startServer: () -> Unit = { EngineMain.main(emptyArray()) },
): AthleticaCommand =
    AthleticaCommand(adminDi)
        .subcommands(
            ServeCommand(startServer),
            AdminCommand()
                .subcommands(
                    OrgCommand()
                        .subcommands(
                            FindCommand(),
                            CreditCommand(),
                            DebitCommand(),
                        ),
                ),
        )
