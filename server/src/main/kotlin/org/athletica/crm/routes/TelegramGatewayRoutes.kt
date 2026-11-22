package org.athletica.crm.routes

import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.telegramgateway.SaveTelegramGatewayTokenRequest
import org.athletica.crm.api.schemas.telegramgateway.SendTelegramGatewayTestCodeRequest
import org.athletica.crm.api.schemas.telegramgateway.SetTelegramGatewayEnabledRequest
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayBalanceSchema
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayCodeOutcome
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayIntegrationSchema
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewaySettingsResponse
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayTestCodeResponse
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.secretMask
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeResult
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.crm.domain.telegramgateway.TelegramGateway
import org.athletica.crm.domain.telegramgateway.TelegramGatewayIntegration
import org.athletica.crm.domain.telegramgateway.TelegramGatewayIntegrations
import org.athletica.crm.storage.Database
import java.security.SecureRandom

/**
 * Регистрирует маршруты настроек Telegram Gateway организации. Токен проверяется у Telegram
 * через [gateway] до открытия транзакции; в ответах — только его маска.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.telegramGatewayRoutes(
    integrations: TelegramGatewayIntegrations,
    gateway: TelegramGateway,
) {
    route("/telegram-gateway") {
        get<TelegramGatewaySettingsResponse>("/settings") {
            db.transaction { TelegramGatewaySettingsResponse(integrations.current()?.toSchema()) }
        }

        post<SaveTelegramGatewayTokenRequest, TelegramGatewaySettingsResponse>("/save-token") { request ->
            val token = gateway.verifiedToken(request.token)
            db.transaction {
                integrations.saveToken(token)
                TelegramGatewaySettingsResponse(integrations.current()?.toSchema())
            }
        }

        post<SetTelegramGatewayEnabledRequest, TelegramGatewaySettingsResponse>("/set-enabled") { request ->
            db.transaction {
                integrations.setEnabled(request.enabled)
                TelegramGatewaySettingsResponse(integrations.current()?.toSchema())
            }
        }

        post<SendTelegramGatewayTestCodeRequest, TelegramGatewayTestCodeResponse>("/test-code") { request ->
            val phone = PhoneDigits.from(request.phone) ?: raise(CommonDomainError("INVALID_PHONE", "Укажите номер телефона"))
            val code = GatewayCode.from(random.nextInt(1_000_000).toString().padStart(6, '0'))
            when (val result = gateway.send(phone, code, GatewayCodeTtl.from(TEST_CODE_TTL_SECONDS))) {
                is GatewayCodeResult.Sent -> TelegramGatewayTestCodeResponse(TelegramGatewayCodeOutcome.SENT, null)
                GatewayCodeResult.Undeliverable -> TelegramGatewayTestCodeResponse(TelegramGatewayCodeOutcome.UNDELIVERABLE, null)
                GatewayCodeResult.NotConfigured -> TelegramGatewayTestCodeResponse(TelegramGatewayCodeOutcome.NOT_CONFIGURED, null)
                is GatewayCodeResult.Failed -> TelegramGatewayTestCodeResponse(TelegramGatewayCodeOutcome.FAILED, result.reason)
            }
        }
    }
}

/** Срок жизни тестового кода в секундах. */
private const val TEST_CODE_TTL_SECONDS = 300

/** Источник тестовых кодов. */
private val random = SecureRandom()

/** Интеграция для показа: токен — маской. */
private fun TelegramGatewayIntegration.toSchema(): TelegramGatewayIntegrationSchema =
    TelegramGatewayIntegrationSchema(
        tokenMask = token.secretMask(),
        enabled = enabled,
        balance = balance?.let { TelegramGatewayBalanceSchema(it.amount, it.at) },
    )
