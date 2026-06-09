package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Клиент организации. Состояние клиента выражено в типах: [ActiveClient] —
 * активный (доступны редактирование и архивирование), [ArchivedClient] —
 * архивный (доступно только восстановление). Недопустимые операции (например,
 * редактирование архивного) невыразимы в типах, а не отлавливаются в рантайме.
 */
sealed interface Client {
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

    /** Документы, прикреплённые к клиенту. */
    val docs: List<ClientDoc>

    /** Идентификатор источника клиента, либо null если не указан. */
    val leadSourceId: LeadSourceId?

    /** Значения кастомных полей клиента. */
    val customFields: List<CustomFieldValue>
}

/**
 * Активный клиент. Поддерживает редактирование, прикрепление документов и
 * перевод в архив.
 */
interface ActiveClient : Client {
    /** Сохраняет изменённые поля и документы клиента в БД. */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Переводит клиента в архив. После этого клиент становится [ArchivedClient]. */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun archive()

    /** Возвращает копию с прикреплённым документом [doc] (без записи в БД). */
    context(ctx: EmployeeRequestContext)
    fun attachDoc(doc: ClientDoc): ActiveClient

    /** Возвращает копию без документа [docId] (без записи в БД). */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun deleteDoc(docId: ClientDocId): ActiveClient

    /** Возвращает копию с новыми значениями полей (без записи в БД). */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId? = null,
        newCustomFields: List<CustomFieldValue> = emptyList(),
    ): ActiveClient
}

/**
 * Архивный клиент. Доступно только восстановление; редактирование и прочие
 * мутации недоступны по типу.
 */
interface ArchivedClient : Client {
    /** Восстанавливает клиента из архива. После этого клиент становится [ActiveClient]. */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun restore()
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
