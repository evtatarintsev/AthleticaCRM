package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
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
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            UPDATE clients
            SET name = :name, avatar_id = :avatarId, birthday = :birthday, gender = :gender::gender,
                lead_source_id = :leadSourceId, custom_fields = :customFields::jsonb
            WHERE id = :id AND org_id = :orgId
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

    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun archive() {
        tr.sql("UPDATE clients SET state = 'ARCHIVED' WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
    }

    context(ctx: EmployeeRequestContext)
    override fun attachDoc(doc: ClientDoc) = copy(docs = docs + doc)

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: ClientDocId) = copy(docs = docs.filterNot { it.id == docId })

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
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
    private val orgId: OrgId,
) : ArchivedClient {
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun restore() {
        tr.sql("UPDATE clients SET state = 'ACTIVE' WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
    }
}
