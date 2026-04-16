package org.athletica.crm.domain.clients

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toClientId
import org.athletica.crm.core.toUploadId
import org.athletica.crm.db.Transaction
import org.athletica.crm.db.asDouble
import org.athletica.crm.db.asInstant
import org.athletica.crm.db.asLocalDateOrNull
import org.athletica.crm.db.asString
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull
import org.athletica.crm.i18n.Messages

class DbClients : Clients {
    context(ctx: RequestContext, tr: Transaction, raise: arrow.core.raise.context.Raise<DomainError>)
    override suspend fun byId(id: ClientId): Client {
        val client =
            tr
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender,
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
                        orgId = ctx.orgId,
                    )
                }
                ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        val groups =
            tr
                .sql(
                    """
                    SELECT g.id, g.name
                    FROM client_groups cg
                    JOIN groups g ON g.id = cg.group_id
                    WHERE cg.client_id = :clientId
                    """.trimIndent(),
                )
                .bind("clientId", id)
                .list { row ->
                    ClientGroup(
                        id = row.asUuid("id"),
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
                        id = row.asUuid("id"),
                        uploadId = row.asUuid("upload_id").toUploadId(),
                        name = row.asString("name"),
                        createdAt = row.asInstant("created_at"),
                    )
                }

        return client.copy(groups = groups, docs = docs)
    }

    context(ctx: RequestContext, tr: Transaction, raise: arrow.core.raise.context.Raise<DomainError>)
    override suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
    ): Client {
        val inserted =
            try {
                tr
                    .sql(
                        """
                        INSERT INTO clients (id, org_id, name, avatar_id, birthday, gender)
                        VALUES (:id, :orgId, :name, :avatarId, :birthday, :gender::gender)
                        """.trimIndent(),
                    )
                    .bind("id", id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", name)
                    .bind("avatarId", avatarId)
                    .bind("birthday", birthday)
                    .bind("gender", gender.name)
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
            orgId = ctx.orgId,
        )
    }
}
