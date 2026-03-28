package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientList(request: ClientListRequest): Either<CommonDomainError, List<ClientListItem>> =
    db
        .sql(
            """
            SELECT c.id, c.name
            FROM clients c
            WHERE org_id = :orgId
            ORDER BY c.name
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
        )
        .bind("userId", ctx.userId.value)
        .bind("orgId", ctx.orgId.value)
        .bind("limit", request.limit)
        .bind("offset", request.offset)
        .list { row, _ ->
            ClientListItem(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
            )
        }
        .right()

context(db: Database, ctx: RequestContext)
suspend fun totalClientsCount(request: ClientListRequest): Either<CommonDomainError, UInt> {
    val count =
        db
            .sql(
                """
                SELECT COUNT(*)
                FROM clients
                WHERE org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { it.get(0, Long::class.java)!! }
            ?: 0L
    return count.toUInt().right()
}
