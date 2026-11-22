package org.athletica.crm.domain.telegramgateway

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Интеграция организации с Telegram Gateway: не больше одной на организацию, все операции — по `ctx.orgId`. */
interface TelegramGatewayIntegrations {
    /** Интеграция организации; `null`, если Gateway не подключали. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun current(): TelegramGatewayIntegration?

    /**
     * Сохраняет проверенный токен [token]: первое подключение создаёт включённую интеграцию,
     * замена токена включённость не меняет.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun saveToken(token: String)

    /** Включает или выключает интеграцию; `TELEGRAM_GATEWAY_NOT_CONNECTED`, если её нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun setEnabled(enabled: Boolean)

    /** Записывает остаток [balance] из ответа Gateway. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun recordBalance(balance: GatewayBalance)
}
