package org.athletica.crm.domain.employees

import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class EmployeePermissions {
    context(tr: Transaction)
    suspend fun byId(employeeId: EmployeeId): EmployeePermission {
        // Получить все роли сотрудника и их permissions
        val rolesByRole = fetchAllRoles().associateBy { it.id }

        val roles =
            tr
                .sql(
                    """
                    SELECT er.role_id
                    FROM employee_roles er
                    WHERE er.employee_id = :employeeId
                    """.trimIndent(),
                )
                .bind("employeeId", employeeId)
                .list { row -> row.asUuid("role_id") }
                .mapNotNull { rolesByRole[it] }

        // Получить override permissions (granted и revoked)
        val permissionOverrides =
            tr
                .sql(
                    """
                    SELECT permission_key, is_granted
                    FROM employee_permission_overrides
                    WHERE employee_id = :employeeId
                    """.trimIndent(),
                )
                .bind("employeeId", employeeId)
                .list { row ->
                    Permission.valueOf(row.asString("permission_key")) to row.asBoolean("is_granted")
                }

        val grantedPermissions = permissionOverrides.filter { it.second }.map { it.first }.toSet()
        val revokedPermissions = permissionOverrides.filter { !it.second }.map { it.first }.toSet()

        return EmployeePermission(
            roles = roles,
            grantedPermissions = grantedPermissions,
            revokedPermissions = revokedPermissions,
        )
    }

    /**
     * Получает все доступные роли с их permissions.
     * Кешируется или получается заново в зависимости от реализации.
     */
    context(tr: Transaction)
    private suspend fun fetchAllRoles(): List<EmployeeRole> {
        // Получить все роли
        val rolesData =
            tr
                .sql(
                    """
                    SELECT r.id, r.name
                    FROM roles r
                    ORDER BY r.name
                    """.trimIndent(),
                )
                .list { row ->
                    row.asUuid("id") to row.asString("name")
                }

        // Получить все permissions для всех ролей одним запросом
        val rolePermissionsMap =
            tr
                .sql(
                    """
                    SELECT role_id, permission_key
                    FROM role_permissions
                    """.trimIndent(),
                )
                .list { row ->
                    row.asUuid("role_id") to Permission.valueOf(row.asString("permission_key"))
                }
                .groupBy({ it.first }, { it.second })

        // Собрать EmployeeRole объекты
        return rolesData.map { (roleId, roleName) ->
            EmployeeRole(
                id = roleId,
                name = roleName,
                permissions = (rolePermissionsMap[roleId] ?: emptyList()).toSet(),
            )
        }
    }
}
