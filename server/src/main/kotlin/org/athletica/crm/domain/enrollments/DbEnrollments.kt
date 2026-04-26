package org.athletica.crm.domain.enrollments

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EnrollmentId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEnrollmentId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asUuid

/** Реализация [Enrollments] с доступом к PostgreSQL через R2DBC. */
class DbEnrollments : Enrollments {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(groupId: GroupId, clientIds: List<ClientId>) {
        verifyGroupOwnership(groupId)

        clientIds.forEach { clientId ->
            try {
                tr
                    .sql(
                        """
                        INSERT INTO enrollments (group_id, client_id)
                        VALUES (:groupId, :clientId)
                        ON CONFLICT (client_id, group_id) WHERE left_at IS NULL DO NOTHING
                        """.trimIndent(),
                    )
                    .bind("groupId", groupId)
                    .bind("clientId", clientId)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
            }
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun remove(groupId: GroupId, clientIds: List<ClientId>) {
        verifyGroupOwnership(groupId)

        clientIds.forEach { clientId ->
            tr
                .sql(
                    """
                    UPDATE enrollments
                    SET left_at = now()
                    WHERE group_id = :groupId AND client_id = :clientId AND left_at IS NULL
                    """.trimIndent(),
                )
                .bind("groupId", groupId)
                .bind("clientId", clientId)
                .execute()
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun activeIn(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): List<Enrollment> =
        tr
            .sql(
                """
                SELECT id, group_id, client_id, enrolled_at, left_at
                FROM enrollments
                WHERE group_id = :groupId
                  AND enrolled_at::date <= :to
                  AND (left_at IS NULL OR left_at::date >= :from)
                ORDER BY enrolled_at
                """.trimIndent(),
            )
            .bind("groupId", groupId)
            .bind("to", to.toJavaLocalDate())
            .bind("from", from.toJavaLocalDate())
            .list { row ->
                Enrollment(
                    id = row.asUuid("id").toEnrollmentId(),
                    groupId = row.asUuid("group_id").toGroupId(),
                    clientId = row.asUuid("client_id").toClientId(),
                    enrolledAt = row.asInstant("enrolled_at"),
                    leftAt = row.asInstantOrNull("left_at"),
                )
            }

    /** Проверяет, что группа [groupId] принадлежит организации из контекста. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun verifyGroupOwnership(groupId: GroupId) {
        tr
            .sql("SELECT id FROM groups WHERE id = :groupId AND org_id = :orgId")
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { _ -> EnrollmentId.new() }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
    }
}
