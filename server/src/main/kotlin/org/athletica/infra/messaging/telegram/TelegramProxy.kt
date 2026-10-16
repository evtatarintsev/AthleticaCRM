package org.athletica.infra.messaging.telegram

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.net.URI
import java.net.URISyntaxException

/** Вид исходящего прокси для запросов к Bot API. */
enum class TelegramProxyType {
    /** HTTP-прокси (CONNECT). */
    HTTP,

    /** SOCKS5-прокси. */
    SOCKS5,
}

/** Логин и пароль прокси. */
data class ProxyCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ProxyCredentials(username=$username, password=***)"
}

/** Исходящий прокси для запросов к Bot API. */
data class TelegramProxy(
    /** Вид прокси. */
    val type: TelegramProxyType,
    /** Хост прокси. */
    val host: String,
    /** Порт прокси. */
    val port: Int,
    /** Логин и пароль; `null` — прокси без аутентификации. */
    val credentials: ProxyCredentials?,
) {
    companion object {
        /**
         * Разбирает список прокси из строки [raw] — URL через запятую, например
         * `socks5://user:pass@host:1080,http://host2:3128`. Пустая строка — пустой список (прямой доступ).
         * Неизвестная схема, отсутствие хоста или порта — ошибка с описанием записи (без пароля).
         */
        fun parseList(raw: String): Either<String, List<TelegramProxy>> {
            val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val parsed = entries.map { parse(it) }
            val error = parsed.firstNotNullOfOrNull { it.leftOrNull() }
            return error?.left() ?: parsed.mapNotNull { it.getOrNull() }.right()
        }

        /** Разбирает одну запись прокси [entry]. */
        private fun parse(entry: String): Either<String, TelegramProxy> {
            val uri =
                try {
                    URI(entry)
                } catch (e: URISyntaxException) {
                    return "Некорректная запись прокси Telegram: ${entry.masked()}".left()
                }
            val type =
                when (uri.scheme?.lowercase()) {
                    "http" -> TelegramProxyType.HTTP
                    "socks5" -> TelegramProxyType.SOCKS5
                    else -> return "Неизвестный тип прокси Telegram: ${entry.masked()}".left()
                }
            val host = uri.host
            if (host.isNullOrBlank() || uri.port <= 0) {
                return "В записи прокси Telegram нет хоста или порта: ${entry.masked()}".left()
            }
            val credentials =
                uri.rawUserInfo?.let { info ->
                    val username = info.substringBefore(':').decoded()
                    val password = info.substringAfter(':', "").decoded()
                    ProxyCredentials(username, password)
                }
            return TelegramProxy(type, host, uri.port, credentials).right()
        }

        /** Декодирует percent-encoding в логине или пароле. */
        private fun String.decoded(): String = java.net.URLDecoder.decode(this, Charsets.UTF_8)

        /** Запись прокси без пароля — для сообщений об ошибке. */
        private fun String.masked(): String = replace(Regex("//([^:/@]*):[^@]*@"), "//$1:***@")
    }
}
