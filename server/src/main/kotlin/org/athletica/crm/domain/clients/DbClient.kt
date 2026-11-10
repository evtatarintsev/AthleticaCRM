package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/**
 * R2DBC-реализация активного клиента. Все запросы фильтруются по `org_id`.
 */
internal data class DbActiveClient(
    override val id: ClientId,
    override val name: String,
    override val avatarId: UploadId?,
    override val birthday: LocalDate?,
    override val gender: Gender,
    override val groups: List<ClientGroup>,
    override val docs: List<ClientDoc>,
    override val leadSourceId: LeadSourceId?,
    override val customFields: List<CustomFieldValue>,
    private val orgId: OrgId,
) : ActiveClient {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        val updated = tr.sql(
            """
            UPDATE clients
            SET name = :name, avatar_id = :avatarId, birthday = :birthday, gender = :gender::gender,
                lead_source_id = :leadSourceId, custom_fields = :customFields::jsonb
            WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE'
            """.trimIndent(),
        )
            .bind("name", name)
            .bind("avatarId", avatarId)
            .bind("birthday", birthday)
            .bind("gender", gender.name)
            .bind("leadSourceId", leadSourceId)
            .bind("customFields", Json.encodeToString(customFields))
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
        ensure(updated == 1L) { clientArchived() }

        tr.sql("DELETE FROM client_docs WHERE client_id = :clientId")
            .bind("clientId", id)
            .execute()

        docs.forEach { doc ->
            tr.sql(
                """
                INSERT INTO client_docs (id, client_id, org_id, upload_id, name, created_at)
                VALUES (:id, :clientId, :orgId, :uploadId, :name, :createdAt)
                """.trimIndent(),
            )
                .bind("id", doc.id)
                .bind("clientId", id)
                .bind("orgId", orgId)
                .bind("uploadId", doc.uploadId)
                .bind("name", doc.name)
                .bind("createdAt", doc.createdAt)
                .execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun archive() {
        val updated =
            tr.sql("UPDATE clients SET state = 'ARCHIVED' WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE'")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        ensure(updated == 1L) { clientAlreadyArchived() }
    }

    context(ctx: EmployeeRequestContext)
    override fun attachDoc(doc: ClientDoc) = copy(docs = docs + doc)

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun deleteDoc(docId: ClientDocId) = copy(docs = docs.filterNot { it.id == docId })

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId?,
        newCustomFields: List<CustomFieldValue>,
    ) = copy(
        name = newName,
        avatarId = newAvatarId,
        birthday = newBirthday,
        gender = newGender,
        leadSourceId = newLeadSourceId,
        customFields = newCustomFields,
    )
}

/**
 * R2DBC-реализация архивного клиента. Поддерживает только восстановление.
 */
internal data class DbArchivedClient(
    override val id: ClientId,
    override val name: String,
    override val avatarId: UploadId?,
    override val birthday: LocalDate?,
    override val gender: Gender,
    override val groups: List<ClientGroup>,
    override val docs: List<ClientDoc>,
    override val leadSourceId: LeadSourceId?,
    override val customFields: List<CustomFieldValue>,
) : ArchivedClient {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun restore() {
        val updated =
            tr.sql("UPDATE clients SET state = 'ACTIVE' WHERE id = :id AND org_id = :orgId AND state = 'ARCHIVED'")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        ensure(updated == 1L) { clientNotArchived() }
    }
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun ActiveClient.lockForWrite() {
    val active =
        tr.sql("SELECT 1 FROM clients WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE' FOR UPDATE")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
    ensure(active != null) { clientArchived() }
}
