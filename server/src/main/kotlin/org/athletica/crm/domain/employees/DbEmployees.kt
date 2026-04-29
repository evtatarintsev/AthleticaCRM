package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.domain.auth.Users
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock

private data class PermissionOverrides(
    val granted: Set<Permission>,
    val revoked: Set<Permission>,
)

class DbEmployees(private val users: Users, private val roles: Roles) : Employees {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
        permissions: EmployeePermission,
        allBranchesAccess: Boolean,
        branchIds: List<BranchId>,
    ): Employee {
        try {
            tr.sql(
                """
                INSERT INTO employees (id, org_id, name, avatar_id, phone_no, email, is_active, all_branches_access)
                VALUES (:id, :orgId, :name, :avatarId, :phoneNo, :email, false, :allBranchesAccess)
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .bind("avatarId", avatarId)
                .bind("phoneNo", phoneNo)
                .bind("email", email)
                .bind("allBranchesAccess", allBranchesAccess)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            if (e.message?.contains("employees_pkey") == true) {
                raise(CommonDomainError("EMPLOYEE_ALREADY_EXISTS", Messages.EmployeeAlreadyExists.localize()))
            }
            throw e
        }
        for (role in permissions.roles) {
            tr.sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                .bind("employeeId", id)
                .bind("roleId", role.id)
                .execute()
        }
        for (permission in permissions.grantedPermissions) {
            tr.sql("INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted) VALUES (:employeeId, :key, true)")
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }
        for (permission in permissions.revokedPermissions) {
            tr.sql("INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted) VALUES (:employeeId, :key, false)")
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }
        if (!allBranchesAccess) {
            branchIds.forEach { branchId ->
                tr.sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                    .bind("employeeId", id)
                    .bind("branchId", branchId)
                    .execute()
            }
        }
        return DbEmployee(
            id = id, userId = null,
            name = name, avatarId = avatarId, isOwner = false, isActive = false,
            joinedAt = Clock.System.now(), phoneNo = phoneNo, email = email,
            users = users,
            permissions = permissions,
            allBranchesAccess = allBranchesAccess,
            branchIds = branchIds,
        )
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId): Employee {
        val roles = rolesByEmployeeId()
        val permissions = permissionsByEmployeeId()
        val branchIdsByEmployee = branchIdsByEmployeeId()
        return tr
            .sql(
                """
                SELECT id, user_id, name, avatar_id, is_owner, is_active, joined_at, phone_no, email, all_branches_access
                FROM employees
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                val employeeId = row.asUuid("id").toEmployeeId()
                val overrides = permissions[employeeId] ?: PermissionOverrides(emptySet(), emptySet())
                DbEmployee(
                    id = employeeId,
                    userId = row.asUuidOrNull("user_id")?.toUserId(),
                    name = row.asString("name"),
                    avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                    isOwner = row.asBoolean("is_owner"),
                    isActive = row.asBoolean("is_active"),
                    joinedAt = row.asInstant("joined_at"),
                    phoneNo = row.asStringOrNull("phone_no"),
                    email = row.asStringOrNull("email")?.toEmailAddress(),
                    users = users,
                    permissions =
                        EmployeePermission(
                            roles = roles[employeeId] ?: emptyList(),
                            grantedPermissions = overrides.granted,
                            revokedPermissions = overrides.revoked,
                        ),
                    allBranchesAccess = row.asBoolean("all_branches_access"),
                    branchIds = branchIdsByEmployee[employeeId] ?: emptyList(),
                )
            }
            ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Employee> {
        val roles = rolesByEmployeeId()
        val permissions = permissionsByEmployeeId()
        val branchIdsByEmployee = branchIdsByEmployeeId()
        return tr
            .sql(
                """
                SELECT e.id, e.name, e.avatar_id, e.is_owner, e.is_active, e.joined_at, e.phone_no, e.email, e.user_id, e.all_branches_access
                FROM employees e
                WHERE e.org_id = :orgId
                ORDER BY e.is_owner DESC, e.name
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                val id = row.asUuid("id").toEmployeeId()
                val overrides = permissions[id] ?: PermissionOverrides(emptySet(), emptySet())
                DbEmployee(
                    id = id,
                    name = row.asString("name"),
                    avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                    isOwner = row.asBoolean("is_owner"),
                    isActive = row.asBoolean("is_active"),
                    joinedAt = row.asInstant("joined_at"),
                    phoneNo = row.asStringOrNull("phone_no"),
                    email = row.asStringOrNull("email")?.toEmailAddress(),
                    userId = row.asUuidOrNull("user_id")?.toUserId(),
                    users = users,
                    permissions =
                        EmployeePermission(
                            roles = roles[id] ?: emptyList(),
                            grantedPermissions = overrides.granted,
                            revokedPermissions = overrides.revoked,
                        ),
                    allBranchesAccess = row.asBoolean("all_branches_access"),
                    branchIds = branchIdsByEmployee[id] ?: emptyList(),
                )
            }
    }

    context(ctx: RequestContext, tr: Transaction)
    private suspend fun permissionsByEmployeeId(): Map<EmployeeId, PermissionOverrides> {
        data class Row(val employeeId: EmployeeId, val permission: Permission, val isGranted: Boolean)

        val rows =
            tr
                .sql(
                    """
                    SELECT po.employee_id, po.permission_key, po.is_granted
                    FROM employee_permission_overrides po
                    JOIN employees e ON e.id = po.employee_id
                    WHERE e.org_id = :orgId
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    Row(
                        employeeId = row.asUuid("employee_id").toEmployeeId(),
                        permission = Permission.valueOf(row.asString("permission_key")),
                        isGranted = row.asBoolean("is_granted"),
                    )
                }

        return rows
            .groupBy { it.employeeId }
            .mapValues { (_, overrides) ->
                PermissionOverrides(
                    granted = overrides.filter { it.isGranted }.map { it.permission }.toSet(),
                    revoked = overrides.filter { !it.isGranted }.map { it.permission }.toSet(),
                )
            }
    }

    context(ctx: RequestContext, tr: Transaction)
    private suspend fun branchIdsByEmployeeId(): Map<EmployeeId, List<BranchId>> =
        tr
            .sql(
                """
                SELECT eb.employee_id, eb.branch_id
                FROM employee_branches eb
                JOIN employees e ON e.id = eb.employee_id
                WHERE e.org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                row.asUuid("employee_id").toEmployeeId() to row.asUuid("branch_id").toBranchId()
            }
            .groupBy({ it.first }, { it.second })

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun rolesByEmployeeId(): Map<EmployeeId, List<EmployeeRole>> {
        val rolesById = roles.list().associateBy { it.id }
        return tr
            .sql(
                """
                SELECT er.employee_id, er.role_id
                FROM employee_roles er
                JOIN employees e ON e.id = er.employee_id
                WHERE e.org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                row.asUuid("employee_id").toEmployeeId() to row.asUuid("role_id")
            }
            .groupBy({ it.first }, { rolesById[it.second] })
            .mapValues { (_, roles) -> roles.filterNotNull() }
    }
}
