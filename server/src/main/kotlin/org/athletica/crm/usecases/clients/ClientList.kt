package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientList(request: ClientListRequest): Either<CommonDomainError, List<ClientListItem>> =
    either {
        data class ClientRow(
            val id: Uuid,
            val name: String,
            val avatarId: Uuid?,
            val birthday: java.time.LocalDate?,
        )

        val rows =
            db
                .sql(
                    """
                    SELECT c.id, c.name, c.avatar_id, c.birthday
                    FROM clients c
                    WHERE c.org_id = :orgId
                    ORDER BY c.name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId.value)
                .list { row ->
                    ClientRow(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        name = row.get("name", String::class.java)!!,
                        avatarId = row.get("avatar_id", java.util.UUID::class.java)?.toKotlinUuid(),
                        birthday = row.get("birthday", java.time.LocalDate::class.java),
                    )
                }

        val groupsByClientId =
            db
                .sql(
                    """
                    SELECT cg.client_id, g.id AS group_id, g.name AS group_name
                    FROM client_groups cg
                    JOIN groups g ON g.id = cg.group_id
                    JOIN clients c ON c.id = cg.client_id
                    WHERE c.org_id = :orgId
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId.value)
                .list { row ->
                    val clientId = row.get("client_id", java.util.UUID::class.java)!!.toKotlinUuid()
                    val group =
                        ClientGroup(
                            id = row.get("group_id", java.util.UUID::class.java)!!.toKotlinUuid(),
                            name = row.get("group_name", String::class.java)!!,
                        )
                    clientId to group
                }
                .groupBy({ it.first }, { it.second })

        rows.map { row ->
            ClientListItem(
                id = row.id,
                name = row.name,
                avatarId = row.avatarId,
                birthday = row.birthday?.toKotlinLocalDate(),
                groups = groupsByClientId[row.id] ?: emptyList(),
            )
        }
    }
