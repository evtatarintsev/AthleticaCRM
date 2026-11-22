package org.athletica.infra.telegram.gateway

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayBalance
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeResult
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.crm.domain.telegramgateway.TelegramGateway
import org.athletica.crm.domain.telegramgateway.TelegramGatewayIntegrations
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/**
 * [TelegramGateway] поверх Gateway API [api]. Настройки организации читаются из [integrations]
 * короткой транзакцией [db], остаток записывается второй: соединение БД не держится, пока ждём Telegram.
 * В журнал пишутся организация, причина и последние 4 цифры номера; код и токен — никогда.
 */
class ProviderTelegramGateway(
    private val db: Database,
    private val integrations: TelegramGatewayIntegrations,
    private val api: TelegramGatewayApi,
    private val clock: Clock = Clock.System,
) : TelegramGateway {
    private val logger = LoggerFactory.getLogger(ProviderTelegramGateway::class.java)

    context(ctx: RequestContext, raise: Raise<AppError>)
    override suspend fun verifiedToken(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            raise(tokenInvalid())
        }
        return when (val result = api.checkSendAbility(trimmed, UNDELIVERABLE_PHONE)) {
            is GatewayResult.Error -> if (result.error == ACCESS_TOKEN_INVALID) raise(tokenInvalid()) else trimmed
            is GatewayResult.Ok -> trimmed
            GatewayResult.Unavailable -> raise(CommonDomainError("TELEGRAM_GATEWAY_UNAVAILABLE", Messages.TelegramGatewayUnavailable.localize()))
        }
    }

    context(ctx: RequestContext, raise: Raise<AppError>)
    override suspend fun send(phone: PhoneDigits, code: GatewayCode, ttl: GatewayCodeTtl): GatewayCodeResult {
        val integration = db.transaction { integrations.current() }
        if (integration == null || !integration.enabled) {
            return GatewayCodeResult.NotConfigured
        }
        val check =
            when (val result = api.checkSendAbility(integration.token, phone)) {
                is GatewayResult.Ok -> result
                is GatewayResult.Error ->
                    return if (result.error == ACCESS_TOKEN_INVALID) {
                        failed(phone, result.error)
                    } else {
                        logger.info("Telegram Gateway: org {} — на номер *{} доставить нельзя ({})", ctx.orgId, phone.tail, result.error)
                        GatewayCodeResult.Undeliverable
                    }
                GatewayResult.Unavailable -> return failed(phone, UNAVAILABLE)
            }
        val sent = api.sendVerificationMessage(integration.token, phone, check.requestId, code, ttl)
        ((sent as? GatewayResult.Ok)?.remainingBalance ?: check.remainingBalance)?.let { amount ->
            db.transaction { integrations.recordBalance(GatewayBalance(amount, clock.now())) }
        }
        return when (sent) {
            is GatewayResult.Ok -> GatewayCodeResult.Sent(sent.requestId)
            is GatewayResult.Error -> failed(phone, sent.error)
            GatewayResult.Unavailable -> failed(phone, UNAVAILABLE)
        }
    }

    /** Исход «ошибка» с причиной [reason]; пишет её в журнал с организацией и концом номера [phone]. */
    context(ctx: RequestContext)
    private fun failed(phone: PhoneDigits, reason: String): GatewayCodeResult.Failed {
        logger.warn("Telegram Gateway: org {} — код на *{} не отправлен ({})", ctx.orgId, phone.tail, reason)
        return GatewayCodeResult.Failed(reason)
    }

    /** Ошибка «токен Gateway недействителен». */
    context(ctx: RequestContext)
    private fun tokenInvalid(): CommonDomainError = CommonDomainError("TELEGRAM_GATEWAY_TOKEN_INVALID", Messages.TelegramGatewayTokenInvalid.localize())

    private companion object {
        /** Строка ошибки Gateway о недействительном токене. */
        const val ACCESS_TOKEN_INVALID = "ACCESS_TOKEN_INVALID"

        /** Причина исхода «ошибка», когда Gateway не ответил. */
        const val UNAVAILABLE = "UNAVAILABLE"

        /** Заведомо недопустимый номер: проверка на него не оплачивается, но проверяет токен. */
        val UNDELIVERABLE_PHONE = PhoneDigits.from("0") ?: error("номер")
    }
}

/** Последние 4 цифры номера — для журнала. */
private val PhoneDigits.tail: String get() = value.takeLast(4)
