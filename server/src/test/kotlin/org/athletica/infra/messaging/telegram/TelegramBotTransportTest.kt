package org.athletica.infra.messaging.telegram

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.SendResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты транспорта [TelegramBotTransport]: отправка и маппинг ошибок Bot API. */
class TelegramBotTransportTest {
    private val config = ChannelConfig.TelegramBot(botToken = "1:t", botUsername = "club_bot")

    private suspend fun sendWith(status: HttpStatusCode, body: String): SendResult = TelegramBotTransport(config, telegramTestApi { respondJson(body, status) }).send("100", "Здравствуйте")

    private fun error(code: Int, description: String) = """{"ok":false,"error_code":$code,"description":"$description"}"""

    @Test
    fun `успех — Sent с message_id`() =
        runTest {
            val result = sendWith(HttpStatusCode.OK, """{"ok":true,"result":{"message_id":321,"date":0,"chat":{"id":100,"type":"private"}}}""")

            assertEquals(SendResult.Sent("321"), result)
        }

    @Test
    fun `429 и 500 — временный сбой`() =
        runTest {
            assertIs<SendResult.TransientFailure>(sendWith(HttpStatusCode.TooManyRequests, error(429, "Too Many Requests: retry after 5")))
            assertIs<SendResult.TransientFailure>(sendWith(HttpStatusCode.InternalServerError, error(500, "Internal Server Error")))
        }

    @Test
    fun `403 — бот заблокирован, без повторов`() =
        runTest {
            val result = sendWith(HttpStatusCode.Forbidden, error(403, "Forbidden: bot was blocked by the user"))

            assertEquals("BOT_BLOCKED", assertIs<SendResult.PermanentFailure>(result).code)
        }

    @Test
    fun `400 chat not found — окончательный отказ`() =
        runTest {
            val result = sendWith(HttpStatusCode.BadRequest, error(400, "Bad Request: chat not found"))

            assertEquals("CHAT_NOT_FOUND", assertIs<SendResult.PermanentFailure>(result).code)
        }

    @Test
    fun `все прокси недоступны — временный сбой`() =
        runTest {
            val transport = TelegramBotTransport(config, telegramTestApi { throw IOException("down") })

            assertIs<SendResult.TransientFailure>(transport.send("100", "текст"))
        }
}
