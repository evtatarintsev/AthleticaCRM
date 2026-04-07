package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает минимальный список групп организации из [ctx] для использования в селекторах.
 * Содержит только [GroupSelectItem.id] и [GroupSelectItem.name], отсортированные по названию.
 */
context(db: Database, ctx: RequestContext)
suspend fun groupListForSelect(): Either<CommonDomainError, List<GroupSelectItem>> =
    db
        .sql(
            """
            SELECT id, name
            FROM groups
            WHERE org_id = :orgId
            ORDER BY name
            """.trimIndent(),
        )
        .bind("orgId", ctx.orgId.value)
        .list { row ->
            GroupSelectItem(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
            )
        }
        .right()
