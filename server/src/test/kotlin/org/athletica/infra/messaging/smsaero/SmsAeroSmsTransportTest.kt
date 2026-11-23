package org.athletica.infra.messaging.smsaero

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.SendResult
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Тесты транспорта SMS Aero [SmsAeroSmsTransport]: маппинг ответов в [SendResult] и журнал. */
class SmsAeroSmsTransportTest {
    private val settings = ChannelConfig.SmsAeroSms(email = "club@example.com", apiKey = "secret-api-key", sign = "Dinamo")

    /** Транспорт, которому SMS Aero отвечает [body] со статусом [status]. */
    private fun transport(body: String, status: HttpStatusCode = HttpStatusCode.OK) = SmsAeroSmsTransport(settings, smsAeroTestApi { respondSmsAero(body, status) })

    /** Ответ SMS Aero с отказом [message]. */
    private fun failure(message: String) = """{"success":false,"data":null,"message":"$message"}"""

    @Test
    fun `успешная отправка — Sent с id SMS Aero, номер нормализован, подпись канала`() =
        runTest {
            var body = ""
            val transport =
                SmsAeroSmsTransport(
                    settings,
                    smsAeroTestApi { request ->
                        body = (request.body as TextContent).text
                        respondSmsAero("""{"success":true,"data":{"id":777},"message":null}""")
                    },
                )

            assertEquals(SendResult.Sent("777"), transport.send("+7 (999) 123-45-67", "Привет"))
            assertEquals("""{"number":"79991234567","text":"Привет","sign":"Dinamo"}""", body)
        }

    @Test
    fun `400 — окончательный отказ с причиной от SMS Aero`() =
        runTest {
            val result = transport(failure("Sign is not approved"), HttpStatusCode.BadRequest).send("79991234567", "т")

            assertEquals(SendResult.PermanentFailure("SMS_AERO_REJECTED", "SMS Aero отклонил сообщение: Sign is not approved"), result)
        }

    @Test
    fun `402 — недостаточно средств`() =
        runTest {
            val result = transport(failure("Not enough money"), HttpStatusCode.PaymentRequired).send("79991234567", "т")

            assertEquals(SendResult.PermanentFailure("SMS_AERO_NO_FUNDS", "Недостаточно средств на балансе SMS Aero"), result)
        }

    @Test
    fun `404 — IP не в белом списке`() =
        runTest {
            val result = transport(failure("IP not allowed"), HttpStatusCode.NotFound).send("79991234567", "т")

            assertEquals("SMS_AERO_IP_FORBIDDEN", (result as SendResult.PermanentFailure).code)
        }

    @Test
    fun `401 — ключ отозван`() =
        runTest {
            val result = transport(failure("Your credentials are incorrect"), HttpStatusCode.Unauthorized).send("79991234567", "т")

            assertEquals("SMS_AERO_AUTH_INVALID", (result as SendResult.PermanentFailure).code)
        }

    @Test
    fun `SMS Aero недоступен — временный сбой`() =
        runTest {
            val result = SmsAeroSmsTransport(settings, smsAeroTestApi { throw IOException("down") }).send("79991234567", "т")

            assertEquals("SMS_AERO_UNAVAILABLE", (result as SendResult.TransientFailure).code)
        }

    @Test
    fun `нет ответа на отправку — окончательный отказ без повтора`() =
        runTest {
            val transport = SmsAeroSmsTransport(settings, smsAeroTestApi { request -> throw HttpRequestTimeoutException(request.url.toString(), 10_000) })

            assertEquals("SMS_AERO_NO_RESPONSE", (transport.send("79991234567", "т") as SendResult.PermanentFailure).code)
        }

    @Test
    fun `в журнале причина и последние 4 цифры, но не ключ, номер и текст`() =
        runTest {
            val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
            val logs = ListAppender<ILoggingEvent>().apply { start() }
            root.addAppender(logs)
            try {
                transport(failure("Not enough money"), HttpStatusCode.PaymentRequired).send("79991234567", "секретный текст")
            } finally {
                root.detachAppender(logs)
            }

            val text = logs.list.joinToString("\n") { it.formattedMessage }
            assertTrue(text.contains("…4567"), text)
            assertTrue(text.contains("Not enough money"), text)
            listOf("secret-api-key", "79991234567", "секретный текст").forEach {
                assertFalse(text.contains(it), "$it попал в журнал: $text")
            }
        }
}
