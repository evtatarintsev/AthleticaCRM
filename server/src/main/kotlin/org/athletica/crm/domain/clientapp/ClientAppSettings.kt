package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean

/** Настройка «Клиентский кабинет включён» организации запроса. */
interface ClientAppSettings {
    /** Включён ли кабинет. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun enabled(): Boolean

    /** Включает или выключает кабинет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun setEnabled(enabled: Boolean)
}

/** Настройка кабинета в колонке `organizations.client_app_enabled`. */
class DbClientAppSettings : ClientAppSettings {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun enabled(): Boolean =
        tr.sql("SELECT client_app_enabled FROM organizations WHERE id = :orgId")
            .bind("orgId", ctx.orgId)
            .firstOrNull { it.asBoolean("client_app_enabled") } ?: false

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun setEnabled(enabled: Boolean) {
        tr.sql("UPDATE organizations SET client_app_enabled = :enabled WHERE id = :orgId")
            .bind("enabled", enabled)
            .bind("orgId", ctx.orgId)
            .execute()
    }
}

/** Декоратор [ClientAppSettings], пишущий изменение настройки в аудит; идентификатор записи — организация. */
class AuditClientAppSettings(
    private val delegate: ClientAppSettings,
    private val audit: AuditLog,
) : ClientAppSettings by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun setEnabled(enabled: Boolean) {
        delegate.setEnabled(enabled)
        audit.logUpdate(ENTITY, ctx.orgId, """{"enabled":$enabled}""")
    }

    private companion object {
        /** Тип сущности в аудите. */
        const val ENTITY = "client_app_settings"
    }
}
