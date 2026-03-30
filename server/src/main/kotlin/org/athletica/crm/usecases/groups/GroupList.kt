package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun groupList(request: GroupListRequest): Either<CommonDomainError, List<GroupListItem>> {
    val nameFilter = request.name
    return db
        .sql(
            """
            SELECT g.id, g.name
            FROM groups g
            WHERE g.org_id = :orgId
            ${if (nameFilter != null) "AND g.name ILIKE :name" else ""}
            ORDER BY g.name
            """.trimIndent(),
        )
        .bind("orgId", ctx.orgId.value)
        .let { if (nameFilter != null) it.bind("name", "%$nameFilter%") else it }
        .list { row, _ ->
            GroupListItem(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
            )
        }
        .right()
}
