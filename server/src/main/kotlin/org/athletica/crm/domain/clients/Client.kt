package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    context(ctx: RequestContext)
    fun attachDoc(doc: ClientDoc): Client

    context(ctx: RequestContext, raise: Raise<DomainError>)
    fun deleteDoc(docId: Uuid): Client
}

@Serializable
data class ClientGroup(
    val id: Uuid,
    val name: String,
)

/** Документ, прикреплённый к клиенту. */

data class ClientDoc(
    val id: Uuid,
    val uploadId: UploadId,
    val name: String,
    val createdAt: Instant,
)

fun clientDoc(uploadId: UploadId, name: String) =
    ClientDoc(
        Uuid.generateV7(),
        uploadId,
        name,
        Clock.System.now(),
    )
