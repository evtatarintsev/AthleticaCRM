package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientDetail(id: Uuid): Either<CommonDomainError, ClientDetailResponse> =
    either {
        val client =
            db
                .sql(
                    """
                    SELECT id, name, avatar_id, birthday, gender
                    FROM clients
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId.value)
                .firstOrNull { row ->
                    ClientDetailResponse(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        name = row.get("name", String::class.java)!!,
                        avatarId = row.get("avatar_id", java.util.UUID::class.java)?.toKotlinUuid(),
                        birthday = row.get("birthday", java.time.LocalDate::class.java)?.toKotlinLocalDate(),
                        gender = Gender.valueOf(row.get("gender", String::class.java)!!),
                        groups = emptyList(),
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
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        name = row.get("name", String::class.java)!!,
                    )
                }

        client.copy(groups = groups)
    }
