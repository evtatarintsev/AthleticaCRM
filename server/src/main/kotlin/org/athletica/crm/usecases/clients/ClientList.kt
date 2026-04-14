package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.toClientId
import org.athletica.crm.core.toUploadId
import org.athletica.crm.db.Database
import org.athletica.crm.db.asDouble
import org.athletica.crm.db.asLocalDateOrNull
import org.athletica.crm.db.asString
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull

context(db: Database, ctx: RequestContext)
suspend fun clientList(request: ClientListRequest): Either<CommonDomainError, List<ClientListItem>> =
    either {
        data class ClientRow(
            val id: ClientId,
            val name: String,
            val avatarId: UploadId?,
            val birthday: LocalDate?,
            val gender: Gender,
            val balance: Double,
        )

        val rows =
            db
                .sql(
                    """
                    SELECT c.id, c.name, c.avatar_id, c.birthday, c.gender,
                           COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = c.id), 0) AS balance
                    FROM clients c
                    WHERE c.org_id = :orgId
                    ORDER BY c.name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    ClientRow(
                        id = row.asUuid("id").toClientId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        birthday = row.asLocalDateOrNull("birthday"),
                        gender = Gender.valueOf(row.asString("gender")),
                        balance = row.asDouble("balance"),
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
                .bind("orgId", ctx.orgId)
                .list { row ->
                    val clientId = row.asUuid("client_id").toClientId()
                    val group =
                        ClientGroup(
                            id = row.asUuid("group_id"),
                            name = row.asString("group_name"),
                        )
                    clientId to group
                }
                .groupBy({ it.first }, { it.second })

        rows.map { row ->
            ClientListItem(
                id = row.id,
                name = row.name,
                avatarId = row.avatarId,
                birthday = row.birthday,
                gender = row.gender,
                groups = groupsByClientId[row.id] ?: emptyList(),
                balance = row.balance,
            )
        }
    }
