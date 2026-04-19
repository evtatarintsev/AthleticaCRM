package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.collections.component1
import kotlin.collections.component2

class DbRoles : Roles {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(role: EmployeeRole) {
        tr.sql("INSERT INTO roles (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", role.id)
            .bind("orgId", ctx.orgId)
            .bind("name", role.name)
            .execute()
        for (permission in role.permissions) {
            tr.sql("INSERT INTO role_permissions (role_id, permission_key) VALUES (:roleId, :key)")
                .bind("roleId", role.id)
                .bind("key", permission.name)
                .execute()
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(role: EmployeeRole) {
        tr.sql("UPDATE roles SET name = :name WHERE id = :id AND org_id = :orgId")
            .bind("id", role.id)
            .bind("orgId", ctx.orgId)
            .bind("name", role.name)
            .execute()
        tr.sql("DELETE FROM role_permissions WHERE role_id = :roleId")
            .bind("roleId", role.id)
            .execute()
        for (permission in role.permissions) {
            tr.sql("INSERT INTO role_permissions (role_id, permission_key) VALUES (:roleId, :key)")
                .bind("roleId", role.id)
                .bind("key", permission.name)
                .execute()
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<EmployeeRole> =
        tr
            .sql(
                """
                SELECT r.id, r.name, rp.permission_key
                FROM roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                WHERE r.org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                Triple(
                    row.asUuid("id"),
                    row.asString("name"),
                    row.asStringOrNull("permission_key"),
                )
            }
            .groupBy({ it.first }, { it.second to it.third })
            .map { (roleId, rows) ->
                EmployeeRole(
                    id = roleId,
                    name = rows.first().first,
                    permissions =
                        rows
                            .mapNotNull { it.second }
                            .map { Permission.valueOf(it) }
                            .toSet(),
                )
            }
}
