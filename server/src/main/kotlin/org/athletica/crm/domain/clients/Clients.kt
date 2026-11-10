package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.storage.Transaction

interface Clients {
    /** Возвращает клиента по [id]. Тип результата отражает состояние: активный или архивный. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: ClientId): Client

    /** Создаёт нового (активного) клиента. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId? = null,
        customFields: List<CustomFieldValue> = emptyList(),
    ): ActiveClient
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Clients.activeById(id: ClientId): ActiveClient =
    when (val client = byId(id)) {
        is ActiveClient -> client
        is ArchivedClient -> raise(clientArchived())
    }

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Clients.archive(id: ClientId) {
    when (val client = byId(id)) {
        is ActiveClient -> client.archive()
        is ArchivedClient -> raise(clientAlreadyArchived())
    }
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Clients.restore(id: ClientId) {
    when (val client = byId(id)) {
        is ArchivedClient -> client.restore()
        is ActiveClient -> raise(clientNotArchived())
    }
}

internal fun clientArchived() = CommonDomainError("CLIENT_ARCHIVED", "Клиент в архиве и недоступен для изменения")

internal fun clientAlreadyArchived() = CommonDomainError("CLIENT_ALREADY_ARCHIVED", "Клиент уже в архиве")

internal fun clientNotArchived() = CommonDomainError("CLIENT_NOT_ARCHIVED", "Клиент не в архиве")
