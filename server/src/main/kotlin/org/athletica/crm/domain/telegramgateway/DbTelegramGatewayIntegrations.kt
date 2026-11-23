package org.athletica.crm.domain.telegramgateway

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asMoneyOrNull
import org.athletica.crm.storage.asString

/** Интеграции с Telegram Gateway в таблице `telegram_gateway_integrations`. */
class DbTelegramGatewayIntegrations : TelegramGatewayIntegrations {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun current(): TelegramGatewayIntegration? =
        tr.sql("SELECT token, enabled, balance, balance_at FROM telegram_gateway_integrations WHERE org_id = :orgId")
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                val amount = row.asMoneyOrNull("balance", Currency.USD)
                val at = row.asInstantOrNull("balance_at")
                TelegramGatewayIntegration(
                    token = row.asString("token"),
                    enabled = row.asBoolean("enabled"),
                    balance = if (amount != null && at != null) GatewayBalance(amount, at) else null,
                )
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun saveToken(token: String) {
        tr.sql(
            """
            INSERT INTO telegram_gateway_integrations (org_id, token, enabled)
            VALUES (:orgId, :token, true)
            ON CONFLICT (org_id) DO UPDATE SET token = EXCLUDED.token
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("token", token)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun setEnabled(enabled: Boolean) {
        val updated =
            tr.sql("UPDATE telegram_gateway_integrations SET enabled = :enabled WHERE org_id = :orgId")
                .bind("orgId", ctx.orgId)
                .bind("enabled", enabled)
                .execute()
        ensure(updated != 0L) { CommonDomainError("TELEGRAM_GATEWAY_NOT_CONNECTED", Messages.TelegramGatewayNotConnected.localize()) }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun recordBalance(balance: GatewayBalance) {
        tr.sql("UPDATE telegram_gateway_integrations SET balance = :balance, balance_at = :at WHERE org_id = :orgId")
            .bind("orgId", ctx.orgId)
            .bind("balance", balance.amount)
            .bind("at", balance.at)
            .execute()
    }
}
