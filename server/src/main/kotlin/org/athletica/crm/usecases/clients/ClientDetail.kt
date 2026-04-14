package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.toClientId
import org.athletica.crm.core.toUploadId
import org.athletica.crm.db.Database
import org.athletica.crm.db.asDouble
import org.athletica.crm.db.asInstant
import org.athletica.crm.db.asLocalDateOrNull
import org.athletica.crm.db.asString
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull
import org.athletica.crm.i18n.Messages

context(db: Database, ctx: RequestContext)
suspend fun clientDetail(id: ClientId): Either<CommonDomainError, ClientDetailResponse> =
    either {
        val client =
            db
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
                    ClientDetailResponse(
                        id = row.asUuid("id").toClientId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        birthday = row.asLocalDateOrNull("birthday"),
                        gender = Gender.valueOf(row.asString("gender")),
                        groups = emptyList(),
                        balance = row.asDouble("balance"),
                    )
                }
                ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        val groups =
            db
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
            db
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

        client.copy(groups = groups, docs = docs)
    }
