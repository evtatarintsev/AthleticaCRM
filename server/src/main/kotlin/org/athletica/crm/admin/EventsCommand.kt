package org.athletica.crm.admin

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import kotlin.uuid.Uuid

/** Группа административных операций над доменными событиями. */
class EventsCommand : SuspendingNoOpCliktCommand(name = "events") {
    override fun help(context: Context): String = "Операции над доменными событиями"
}

/** Возвращает в обработку доменные события, исчерпавшие попытки. */
class RetryFailedCommand : SuspendingCliktCommand(name = "retry-failed") {
    private val di by requireObject<AdminDi>()

    private val orgId by option("--org", help = "Только события организации").convert { it.toUuidOption() }

    private val eventId by option("--event", help = "Только одно событие").convert { it.toUuidOption() }

    override fun help(context: Context): String = "Перезапустить неуспешные доменные события"

    override suspend fun run() {
        val count = di.failedEvents.retry(orgId, eventId)
        echo("Возвращено в обработку событий: $count")
    }
}

/** Разбирает строку опции как UUID; иначе — ошибка разбора аргументов. */
private fun String.toUuidOption(): Uuid =
    try {
        Uuid.parse(this)
    } catch (e: IllegalArgumentException) {
        throw BadParameterValue("ожидается UUID, получено \"$this\"")
    }
