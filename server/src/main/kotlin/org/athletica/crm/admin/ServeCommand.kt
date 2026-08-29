package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context

/**
 * Режим веб-сервера: поднимает HTTP-сервер Ktor.
 * Собственных опций не имеет — источником конфигурации остаются `application.conf`
 * и переменные окружения. [startServer] — запуск сервера.
 */
class ServeCommand(
    private val startServer: () -> Unit,
) : SuspendingCliktCommand(name = "serve") {
    override fun help(context: Context): String = "Запустить веб-сервер"

    override suspend fun run() = startServer()
}
