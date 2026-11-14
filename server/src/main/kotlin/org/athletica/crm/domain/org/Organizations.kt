package org.athletica.crm.domain.org

import arrow.core.raise.Raise
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.storage.Transaction

interface Organizations {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun current(): Organization
}

interface Organization {
    val id: OrgId
    val name: String
    val timezone: String

    /**
     * Валюта, в которой ведутся все денежные операции организации.
     * Задаётся при регистрации и не меняется (конвертация курса не поддерживается).
     */
    val currency: Currency

    context(ctx: RequestContext, raise: Raise<AppError>)
    suspend fun withNew(newName: NonBlankName, newTimezone: String): Organization

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save()
}
