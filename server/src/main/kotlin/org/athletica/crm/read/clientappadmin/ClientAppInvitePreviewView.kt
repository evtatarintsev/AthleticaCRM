package org.athletica.crm.read.clientappadmin

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientappadmin.ClientAppInvitePreviewResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong

/** Подготовка массового приглашения в клиентский кабинет. */
interface ClientAppInvitePreviewView {
    /**
     * Среди активных клиентов организации ([groupIds] `null`) или участников групп [groupIds]: сколько готовы
     * (есть номер, который ещё не приглашали) и сколько без телефона.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun preview(groupIds: List<GroupId>?): ClientAppInvitePreviewResponse
}

/** Подготовка массового приглашения одним SQL по клиентам, контактам и доступам. */
class DbClientAppInvitePreviewView : ClientAppInvitePreviewView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun preview(groupIds: List<GroupId>?): ClientAppInvitePreviewResponse =
        tr.sql(
            """
            WITH phones AS (
                SELECT c.id AS client_id, normalized_phone(cc.value) AS digits
                FROM clients c
                LEFT JOIN client_contacts cc
                    ON cc.client_id = c.id AND cc.org_id = c.org_id AND cc.type = 'PHONE' AND normalized_phone(cc.value) <> ''
                WHERE c.org_id = :orgId AND c.state = 'ACTIVE'
                  AND (:allGroups OR EXISTS (
                      SELECT 1 FROM enrollments e
                      WHERE e.client_id = c.id AND e.left_at IS NULL AND e.group_id = ANY(:groupIds)
                  ))
            )
            SELECT
                count(DISTINCT p.client_id) FILTER (
                    WHERE p.digits IS NOT NULL AND NOT EXISTS (
                        SELECT 1 FROM client_app_access a
                        WHERE a.org_id = :orgId AND a.client_id = p.client_id AND a.phone_digits = p.digits
                    )
                ) AS ready,
                count(DISTINCT p.client_id) FILTER (
                    WHERE NOT EXISTS (SELECT 1 FROM phones q WHERE q.client_id = p.client_id AND q.digits IS NOT NULL)
                ) AS without_phone
            FROM phones p
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("allGroups", groupIds == null)
            .bind("groupIds", groupIds.orEmpty())
            .firstOrNull { ClientAppInvitePreviewResponse(it.asLong("ready").toInt(), it.asLong("without_phone").toInt()) }
            ?: ClientAppInvitePreviewResponse(0, 0)
}
