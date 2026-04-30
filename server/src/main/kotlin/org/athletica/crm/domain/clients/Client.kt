package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

interface Client {
    /** Уникальный идентификатор клиента. */
    val id: ClientId

    /** Отображаемое имя клиента. */
    val name: String

    /** Идентификатор загрузки аватарки клиента, либо null если аватарка не задана. */
    val avatarId: UploadId?

    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate?

    /** Пол клиента. */
    val gender: Gender

    /** Группы в которых состоит клиент. */
    val groups: List<ClientGroup>

    /** Баланс личного счёта клиента (отрицательный — задолженность). */
    val balance: Double

    /** Документы, прикреплённые к клиенту. */
    val docs: List<ClientDoc>

    /** Идентификатор источника клиента, либо null если не указан. */
    val leadSourceId: LeadSourceId?

    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    context(ctx: RequestContext)
    fun attachDoc(doc: ClientDoc): Client

    context(ctx: RequestContext, raise: Raise<DomainError>)
    fun deleteDoc(docId: ClientDocId): Client

    context(ctx: RequestContext, raise: Raise<DomainError>)
    fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId? = null,
    ): Client
}

@Serializable
data class ClientGroup(
    val id: GroupId,
    val name: String,
)

/** Документ, прикреплённый к клиенту. */

data class ClientDoc(
    val id: ClientDocId,
    val uploadId: UploadId,
    val name: String,
    val createdAt: Instant,
)

fun clientDoc(uploadId: UploadId, name: String) =
    ClientDoc(
        ClientDocId.new(),
        uploadId,
        name,
        Clock.System.now(),
    )
