package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [EmployeeDetailView] поверх PostgreSQL. Роли, выдачи, отзывы прав и филиалы
 * приходят JSONB-агрегатами, поэтому карточка собирается одним запросом.
 */
class DbEmployeeDetailView : EmployeeDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId): EmployeeDetailResponse =
        tr
            .sql(SQL)
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toDetailResponse() }
            ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))

    /** Собирает карточку из строки выборки. */
    private fun Row.toDetailResponse(): EmployeeDetailResponse {
        val allBranchesAccess = asBoolean("all_branches_access")
        return EmployeeDetailResponse(
            id = asUuid("id").toEmployeeId(),
            name = asString("name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            isOwner = asBoolean("is_owner"),
            isActive = asBoolean("is_active"),
            joinedAt = asInstant("joined_at"),
            roles = Json.decodeFromString<List<EmployeeRole>>(asString("roles")),
            phoneNo = asStringOrNull("phone_no"),
            email = asStringOrNull("email"),
            grantedPermissions = Json.decodeFromString<Set<UserPermission>>(asString("granted")),
            revokedPermissions = Json.decodeFromString<Set<UserPermission>>(asString("revoked")),
            allBranchesAccess = allBranchesAccess,
            branchIds =
                if (allBranchesAccess) {
                    emptyList()
                } else {
                    Json.decodeFromString<List<BranchId>>(asString("branch_ids"))
                },
        )
    }

    private companion object {
        val SQL =
            """
            SELECT
                e.id, e.name, e.avatar_id, e.is_owner, e.is_active, e.joined_at,
                e.phone_no, e.email, e.all_branches_access,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', r.id, 'name', r.name) ORDER BY r.name), '[]'::jsonb)
                   FROM employee_roles er JOIN roles r ON r.id = er.role_id AND r.org_id = e.org_id
                  WHERE er.employee_id = e.id) AS roles,
                (SELECT COALESCE(jsonb_agg(po.permission_key), '[]'::jsonb)
                   FROM employee_permission_overrides po
                  WHERE po.employee_id = e.id AND po.is_granted) AS granted,
                (SELECT COALESCE(jsonb_agg(po.permission_key), '[]'::jsonb)
                   FROM employee_permission_overrides po
                  WHERE po.employee_id = e.id AND NOT po.is_granted) AS revoked,
                (SELECT COALESCE(jsonb_agg(eb.branch_id), '[]'::jsonb)
                   FROM employee_branches eb
                  WHERE eb.employee_id = e.id) AS branch_ids
            FROM employees e
            WHERE e.id = :id AND e.org_id = :orgId
            """.trimIndent()
    }
}
