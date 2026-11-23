package org.athletica.infra.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.employeeContext
import org.athletica.infra.messaging.smsaero.SMS_AERO_AUTH_FAIL
import org.athletica.infra.messaging.smsaero.SMS_AERO_AUTH_OK
import org.athletica.infra.messaging.smsaero.respondSmsAero
import org.athletica.infra.messaging.smsaero.signListPage
import org.athletica.infra.messaging.smsaero.smsAeroMethod
import org.athletica.infra.messaging.smsaero.smsAeroTestApi
import org.athletica.infra.messaging.telegram.respondJson
import org.athletica.infra.messaging.telegram.telegramTestApi
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты проверки токена бота при сохранении интеграции [ProviderChannelConfigVerifier]. */
class ProviderChannelConfigVerifierTest {
    private val noTelegram = telegramTestApi { error("Bot API не вызывается") }
    private val noSmsAero = smsAeroTestApi { error("SMS Aero не вызывается") }
    private val smsAeroConfig = ChannelConfig.SmsAeroSms(email = " club@example.com ", apiKey = " key ", sign = " Dinamo ")

    /** Верификатор, у которого SMS Aero отвечает [auth] на `/auth` и [signs] на `/sign/list`. */
    private fun smsAeroVerifier(auth: String = SMS_AERO_AUTH_OK, authStatus: HttpStatusCode = HttpStatusCode.OK, signs: String = signListPage("Dinamo", 1)) =
        ProviderChannelConfigVerifier(
            noTelegram,
            smsAeroTestApi { request ->
                when (request.smsAeroMethod) {
                    "auth" -> respondSmsAero(auth, authStatus)
                    else -> respondSmsAero(signs)
                }
            },
        )

    private suspend fun verify(verifier: ProviderChannelConfigVerifier, config: ChannelConfig): Either<AppError, ChannelConfig> = either { context(employeeContext(orgId = OrgId.new())) { verifier.verified(config) } }

    @Test
    fun `корректный токен — конфиг с именем бота из getMe`() =
        runTest {
            val verifier =
                ProviderChannelConfigVerifier(
                    telegramTestApi { respondJson("""{"ok":true,"result":{"id":1,"is_bot":true,"first_name":"Club","username":"club_bot"}}""") },
                    noSmsAero,
                )

            val result = verify(verifier, ChannelConfig.TelegramBot(botToken = " 1:token ", botUsername = "spoofed"))

            assertEquals(ChannelConfig.TelegramBot(botToken = "1:token", botUsername = "club_bot"), assertIs<Either.Right<ChannelConfig>>(result).value)
        }

    @Test
    fun `токен отклонён Bot API — TELEGRAM_TOKEN_INVALID`() =
        runTest {
            val verifier =
                ProviderChannelConfigVerifier(
                    telegramTestApi { respondJson("""{"ok":false,"error_code":401,"description":"Unauthorized"}""", HttpStatusCode.Unauthorized) },
                    noSmsAero,
                )

            val result = verify(verifier, ChannelConfig.TelegramBot(botToken = "1:bad"))

            assertEquals("TELEGRAM_TOKEN_INVALID", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `сеть недоступна — TELEGRAM_UNAVAILABLE`() =
        runTest {
            val verifier = ProviderChannelConfigVerifier(telegramTestApi { throw IOException("down") }, noSmsAero)

            val result = verify(verifier, ChannelConfig.TelegramBot(botToken = "1:token"))

            assertEquals("TELEGRAM_UNAVAILABLE", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `конфиг SMS возвращается как есть без обращения к Bot API`() =
        runTest {
            val config = ChannelConfig.SmscSms(login = "l", password = "p")
            val verifier = ProviderChannelConfigVerifier(noTelegram, noSmsAero)

            assertEquals(config, assertIs<Either.Right<ChannelConfig>>(verify(verifier, config)).value)
        }

    @Test
    fun `SMS Aero принял ключ и подпись одобрена — конфиг без пробелов по краям`() =
        runTest {
            val result = verify(smsAeroVerifier(), smsAeroConfig)

            assertEquals(ChannelConfig.SmsAeroSms("club@example.com", "key", "Dinamo"), assertIs<Either.Right<ChannelConfig>>(result).value)
        }

    @Test
    fun `SMS Aero отклонил учётные данные — SMS_AERO_AUTH_INVALID`() =
        runTest {
            val result = verify(smsAeroVerifier(auth = SMS_AERO_AUTH_FAIL, authStatus = HttpStatusCode.Unauthorized), smsAeroConfig)

            assertEquals("SMS_AERO_AUTH_INVALID", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `подписи нет в аккаунте — SMS_AERO_SIGN_NOT_FOUND`() =
        runTest {
            val result = verify(smsAeroVerifier(signs = signListPage("dinamo", 1)), smsAeroConfig)

            assertEquals("SMS_AERO_SIGN_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `подпись на модерации — SMS_AERO_SIGN_NOT_APPROVED с именем подписи`() =
        runTest {
            val result = verify(smsAeroVerifier(signs = signListPage("Dinamo", 0)), smsAeroConfig)

            val error = assertIs<Either.Left<AppError>>(result).value
            assertEquals("SMS_AERO_SIGN_NOT_APPROVED", error.code)
            assertEquals("Подпись Dinamo ещё не одобрена в SMS Aero", error.message)
        }

    @Test
    fun `SMS Aero не ответил — SMS_AERO_UNAVAILABLE`() =
        runTest {
            val verifier = ProviderChannelConfigVerifier(noTelegram, smsAeroTestApi { throw IOException("timeout") })

            assertEquals("SMS_AERO_UNAVAILABLE", assertIs<Either.Left<AppError>>(verify(verifier, smsAeroConfig)).value.code)
        }

    @Test
    fun `пустая подпись SMS Aero — ошибка поля без обращения к SMS Aero`() =
        runTest {
            val result = verify(ProviderChannelConfigVerifier(noTelegram, noSmsAero), smsAeroConfig.copy(sign = "  "))

            assertEquals("SMS_AERO_FIELDS_REQUIRED", assertIs<Either.Left<AppError>>(result).value.code)
        }
}
