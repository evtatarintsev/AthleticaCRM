package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Clients {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ClientId): Client

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
    ): Client
}
