package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Clients {
    /** Возвращает клиента по [id]. Тип результата отражает состояние: активный или архивный. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ClientId): Client

    /** Создаёт нового (активного) клиента. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId? = null,
        customFields: List<CustomFieldValue> = emptyList(),
    ): ActiveClient

    /**
     * Возвращает клиентов организации. По умолчанию — только активные;
     * при [archived] = `true` — только архивные.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(archived: Boolean = false): List<Client>
}
