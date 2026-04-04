package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientDetail(id: Uuid): Either<CommonDomainError, ClientDetailResponse> =
    either {
        db
            .sql(
                """
                SELECT id, name, avatar_id, birthday
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
                    birthday = row.get("birthday", java.time.LocalDate::class.java)?.toString(),
                )
            }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", "Клиент не найден"))
    }
