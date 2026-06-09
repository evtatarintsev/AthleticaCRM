package org.athletica.crm.domain.clients

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toClientDocId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toLeadSourceId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

class DbClients : Clients {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ClientId): Client {
        val scalar =
            tr
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender, lead_source_id, custom_fields, state
                    FROM clients
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.toScalarClient() }
                ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        val groups =
            tr
                .sql(
                    """
                    SELECT g.id, g.name
                    FROM enrollments e
                    JOIN groups g ON g.id = e.group_id
                    WHERE e.client_id = :clientId AND e.left_at IS NULL
                    """.trimIndent(),
                )
                .bind("clientId", id)
                .list { row ->
                    ClientGroup(
                        id = row.asUuid("id").toGroupId(),
                        name = row.asString("name"),
                    )
                }

        val docs =
            tr
                .sql(
                    """
                    SELECT id, upload_id, name, created_at
                    FROM client_docs
                    WHERE client_id = :clientId
                    ORDER BY created_at DESC
                    """.trimIndent(),
                )
                .bind("clientId", id)
                .list { row -> row.toClientDoc() }

        return scalar.toClient(groups, docs)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId?,
        customFields: List<CustomFieldValue>,
    ): ActiveClient {
        val inserted =
            try {
                tr
                    .sql(
                        """
                        INSERT INTO clients (id, org_id, name, avatar_id, birthday, gender, lead_source_id, custom_fields)
                        VALUES (:id, :orgId, :name, :avatarId, :birthday, :gender::gender, :leadSourceId, :customFields::jsonb)
                        """.trimIndent(),
                    )
                    .bind("id", id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", name)
                    .bind("avatarId", avatarId)
                    .bind("birthday", birthday)
                    .bind("gender", gender.name)
                    .bind("leadSourceId", leadSourceId)
                    .bind("customFields", Json.encodeToString(customFields))
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("CLIENT_ALREADY_EXISTS", Messages.ClientAlreadyExists.localize()))
            }

        if (inserted == 0L) raise(CommonDomainError("CLIENT_ALREADY_EXISTS", Messages.ClientAlreadyExists.localize()))

        return DbActiveClient(
            id = id,
            name = name,
            avatarId = avatarId,
            birthday = birthday,
            gender = gender,
            groups = emptyList(),
            docs = emptyList(),
            leadSourceId = leadSourceId,
            customFields = customFields,
            orgId = ctx.orgId,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(archived: Boolean): List<Client> {
        val scalars =
            tr
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender, lead_source_id, custom_fields, state
                    FROM clients
                    WHERE org_id = :orgId AND state = :state::client_state
                    ORDER BY name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("state", if (archived) "ARCHIVED" else "ACTIVE")
                .list { row -> row.toScalarClient() }

        val groupsByClientId =
            tr
                .sql(
                    """
                    SELECT e.client_id, g.id, g.name
                    FROM enrollments e
                    JOIN groups g ON g.id = e.group_id
                    WHERE g.org_id = :orgId AND e.left_at IS NULL
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    val clientId = row.asUuid("client_id").toClientId()
                    val group =
                        ClientGroup(
                            id = row.asUuid("id").toGroupId(),
                            name = row.asString("name"),
                        )
                    clientId to group
                }
                .groupBy({ it.first }, { it.second })

        val docsByClientId =
            tr
                .sql(
                    """
                    SELECT client_id, id, upload_id, name, created_at
                    FROM client_docs
                    WHERE client_id IN (SELECT id FROM clients WHERE org_id = :orgId)
                    ORDER BY created_at DESC
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    val clientId = row.asUuid("client_id").toClientId()
                    clientId to row.toClientDoc()
                }
                .groupBy({ it.first }, { it.second })

        return scalars.map { scalar ->
            scalar.toClient(
                groups = groupsByClientId[scalar.id] ?: emptyList(),
                docs = docsByClientId[scalar.id] ?: emptyList(),
            )
        }
    }

    /** Скалярные поля строки клиента без связанных коллекций (группы, документы). */
    private data class ScalarClient(
        val id: ClientId,
        val name: String,
        val avatarId: UploadId?,
        val birthday: LocalDate?,
        val gender: Gender,
        val leadSourceId: LeadSourceId?,
        val customFields: List<CustomFieldValue>,
        val archived: Boolean,
    )

    /** Собирает доменного клиента нужного состояния из скаляров и связанных коллекций. */
    context(ctx: EmployeeRequestContext)
    private fun ScalarClient.toClient(groups: List<ClientGroup>, docs: List<ClientDoc>): Client =
        if (archived) {
            DbArchivedClient(id, name, avatarId, birthday, gender, groups, docs, leadSourceId, customFields, ctx.orgId)
        } else {
            DbActiveClient(id, name, avatarId, birthday, gender, groups, docs, leadSourceId, customFields, ctx.orgId)
        }

    private fun io.r2dbc.spi.Row.toScalarClient(): ScalarClient =
        ScalarClient(
            id = asUuid("id").toClientId(),
            name = asString("name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            birthday = asLocalDateOrNull("birthday"),
            gender = Gender.valueOf(asString("gender")),
            leadSourceId = asUuidOrNull("lead_source_id")?.toLeadSourceId(),
            customFields = Json.decodeFromString(asString("custom_fields")),
            archived = asString("state") == "ARCHIVED",
        )

    private fun io.r2dbc.spi.Row.toClientDoc(): ClientDoc =
        ClientDoc(
            id = asUuid("id").toClientDocId(),
            uploadId = asUuid("upload_id").toUploadId(),
            name = asString("name"),
            createdAt = asInstant("created_at"),
        )
}
