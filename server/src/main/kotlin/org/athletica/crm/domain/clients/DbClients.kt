package org.athletica.crm.domain.clients

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
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
import org.athletica.crm.storage.asDouble
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

class DbClients : Clients {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ClientId): Client {
        val client =
            tr
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender, lead_source_id,
                           COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = clients.id), 0) AS balance
                    FROM clients
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    DbClient(
                        id = row.asUuid("id").toClientId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        birthday = row.asLocalDateOrNull("birthday"),
                        gender = Gender.valueOf(row.asString("gender")),
                        groups = emptyList(),
                        balance = row.asDouble("balance"),
                        docs = emptyList(),
                        leadSourceId = row.asUuidOrNull("lead_source_id")?.toLeadSourceId(),
                        orgId = ctx.orgId,
                    )
                }
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
                .list { row ->
                    ClientDoc(
                        id = row.asUuid("id").toClientDocId(),
                        uploadId = row.asUuid("upload_id").toUploadId(),
                        name = row.asString("name"),
                        createdAt = row.asInstant("created_at"),
                    )
                }

        return client.copy(groups = groups, docs = docs)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId?,
    ): Client {
        val inserted =
            try {
                tr
                    .sql(
                        """
                        INSERT INTO clients (id, org_id, name, avatar_id, birthday, gender, lead_source_id)
                        VALUES (:id, :orgId, :name, :avatarId, :birthday, :gender::gender, :leadSourceId)
                        """.trimIndent(),
                    )
                    .bind("id", id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", name)
                    .bind("avatarId", avatarId)
                    .bind("birthday", birthday)
                    .bind("gender", gender.name)
                    .bind("leadSourceId", leadSourceId)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("CLIENT_ALREADY_EXISTS", Messages.ClientAlreadyExists.localize()))
            }

        if (inserted == 0L) raise(CommonDomainError("CLIENT_ALREADY_EXISTS", Messages.ClientAlreadyExists.localize()))

        return DbClient(
            id = id,
            name = name,
            avatarId = avatarId,
            birthday = birthday,
            gender = gender,
            groups = emptyList(),
            balance = 0.0,
            docs = emptyList(),
            leadSourceId = leadSourceId,
            orgId = ctx.orgId,
        )
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Client> {
        val clients =
            tr
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender, lead_source_id,
                           COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = clients.id), 0) AS balance
                    FROM clients
                    WHERE org_id = :orgId
                    ORDER BY name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    DbClient(
                        id = row.asUuid("id").toClientId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        birthday = row.asLocalDateOrNull("birthday"),
                        gender = Gender.valueOf(row.asString("gender")),
                        groups = emptyList(),
                        balance = row.asDouble("balance"),
                        docs = emptyList(),
                        leadSourceId = row.asUuidOrNull("lead_source_id")?.toLeadSourceId(),
                        orgId = ctx.orgId,
                    )
                }

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
                    val doc =
                        ClientDoc(
                            id = row.asUuid("id").toClientDocId(),
                            uploadId = row.asUuid("upload_id").toUploadId(),
                            name = row.asString("name"),
                            createdAt = row.asInstant("created_at"),
                        )
                    clientId to doc
                }
                .groupBy({ it.first }, { it.second })

        return clients.map { client ->
            client.copy(
                groups = groupsByClientId[client.id] ?: emptyList(),
                docs = docsByClientId[client.id] ?: emptyList(),
            )
        }
    }
}
