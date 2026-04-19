package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.uuid.Uuid

internal data class DbClient(
    override val id: ClientId,
    override val name: String,
    override val avatarId: UploadId?,
    override val birthday: LocalDate?,
    override val gender: Gender,
    override val groups: List<ClientGroup>,
    override val balance: Double,
    override val docs: List<ClientDoc>,
    private val orgId: OrgId,
) : Client {
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            UPDATE clients
            SET name = :name, avatar_id = :avatarId, birthday = :birthday, gender = :gender::gender
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("name", name)
            .bind("avatarId", avatarId)
            .bind("birthday", birthday)
            .bind("gender", gender.name)
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

    context(ctx: RequestContext)
    override fun attachDoc(doc: ClientDoc) = copy(docs = docs + doc)

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: Uuid) = copy(docs = docs.filterNot { it.id == docId })

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
    ) = copy(name = newName, avatarId = newAvatarId, birthday = newBirthday, gender = newGender)
}
