package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlinx.datetime.toKotlinLocalDate
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientList(request: ClientListRequest): Either<CommonDomainError, List<ClientListItem>> =
    db
        .sql(
            """
            SELECT c.id, c.name, c.avatar_id, c.birthday
            FROM clients c
            WHERE org_id = :orgId
            ORDER BY c.name
            """.trimIndent(),
        )
        .bind("userId", ctx.userId.value)
        .bind("orgId", ctx.orgId.value)
        .list { row, _ ->
            ClientListItem(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
                avatarId = row.get("avatar_id", java.util.UUID::class.java)?.toKotlinUuid(),
                birthday = row.get("birthday", java.time.LocalDate::class.java)?.toKotlinLocalDate(),
            )
        }
        .right()
