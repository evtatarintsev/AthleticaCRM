package org.athletica.infra.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.employeeContext
import org.athletica.infra.messaging.telegram.respondJson
import org.athletica.infra.messaging.telegram.telegramTestApi
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты проверки токена бота при сохранении интеграции [ProviderChannelConfigVerifier]. */
class ProviderChannelConfigVerifierTest {
    private suspend fun verify(verifier: ProviderChannelConfigVerifier, config: ChannelConfig): Either<DomainError, ChannelConfig> = either { context(employeeContext(orgId = OrgId.new())) { verifier.verified(config) } }

    @Test
    fun `корректный токен — конфиг с именем бота из getMe`() =
        runTest {
            val verifier =
                ProviderChannelConfigVerifier(
                    telegramTestApi { respondJson("""{"ok":true,"result":{"id":1,"is_bot":true,"first_name":"Club","username":"club_bot"}}""") },
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
                )

            val result = verify(verifier, ChannelConfig.TelegramBot(botToken = "1:bad"))

            assertEquals("TELEGRAM_TOKEN_INVALID", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `сеть недоступна — TELEGRAM_UNAVAILABLE`() =
        runTest {
            val verifier = ProviderChannelConfigVerifier(telegramTestApi { throw IOException("down") })

            val result = verify(verifier, ChannelConfig.TelegramBot(botToken = "1:token"))

            assertEquals("TELEGRAM_UNAVAILABLE", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `конфиг SMS возвращается как есть без обращения к Bot API`() =
        runTest {
            val config = ChannelConfig.SmscSms(login = "l", password = "p")
            val verifier = ProviderChannelConfigVerifier(telegramTestApi { error("Bot API не вызывается") })

            assertEquals(config, assertIs<Either.Right<ChannelConfig>>(verify(verifier, config)).value)
        }
}
