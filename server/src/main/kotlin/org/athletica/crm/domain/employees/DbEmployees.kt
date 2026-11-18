package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission
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
import kotlin.uuid.Uuid

/** Роль сотрудника с правами в JSONB-агрегате [DbEmployees.byIds]. */
@Serializable
private class RoleRow(
    /** Идентификатор роли. */
    val id: Uuid,
    /** Название роли. */
    val name: String,
    /** Права роли. */
    val permissions: Set<UserPermission>,
)

/** Репозиторий сотрудников поверх PostgreSQL; [roles] — каталог ролей для назначений при записи. */
class DbEmployees(private val users: Users, private val roles: Roles) : Employees {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(
        id: EmployeeId,
        name: NonBlankName,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
        permissions: EmployeePermissionSelection,
        availableBranches: EmployeeBranchAccess,
    ): Employee {
        val permissionSettings = roles.resolve(permissions)
        val (allBranchesAccess, branchIds) =
            when (availableBranches) {
                is EmployeeBranchAccess.All -> true to emptyList()
                is EmployeeBranchAccess.Selected -> false to resolveEmployeeBranchIds(availableBranches.ids)
            }

        tr.sql(
            """
            INSERT INTO employees (id, org_id, name, avatar_id, phone_no, email, is_active, all_branches_access)
            VALUES (:id, :orgId, :name, :avatarId, :phoneNo, :email, false, :allBranchesAccess)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("name", name.value)
            .bind("avatarId", avatarId)
            .bind("phoneNo", phoneNo)
            .bind("email", email)
            .bind("allBranchesAccess", allBranchesAccess)
            .onViolation("employees_pkey") { CommonDomainError("EMPLOYEE_ALREADY_EXISTS", Messages.EmployeeAlreadyExists.localize()) }
            .execute()
        for (role in permissionSettings.roles) {
            tr.sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                .bind("employeeId", id)
                .bind("roleId", role.id)
                .execute()
        }
        for (permission in permissionSettings.grantedPermissions) {
            tr.sql("INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted) VALUES (:employeeId, :key, true)")
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }
        for (permission in permissionSettings.revokedPermissions) {
            tr.sql("INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted) VALUES (:employeeId, :key, false)")
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }
        branchIds.forEach { branchId ->
            tr.sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                .bind("employeeId", id)
                .bind("branchId", branchId)
                .execute()
        }
        return DbEmployee(
            id = id, userId = null,
            name = name.value, avatarId = avatarId, isOwner = false, isActive = false,
            joinedAt = Clock.System.now(), phoneNo = phoneNo, email = email,
            users = users,
            permissionSettings = permissionSettings,
            roleCatalog = roles,
            orgId = ctx.orgId,
            availableBranches = availableBranches,
        )
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: EmployeeId): Employee =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byIds(ids: List<EmployeeId>): List<Employee> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }
        val result =
            tr
                .sql(BY_IDS_SQL)
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list { row ->
                    DbEmployee(
                        id = row.asUuid("id").toEmployeeId(),
                        userId = row.asUuidOrNull("user_id")?.toUserId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        isOwner = row.asBoolean("is_owner"),
                        isActive = row.asBoolean("is_active"),
                        joinedAt = row.asInstant("joined_at"),
                        phoneNo = row.asStringOrNull("phone_no"),
                        email = row.asStringOrNull("email")?.toEmailAddress(),
                        users = users,
                        roleCatalog = roles,
                        orgId = ctx.orgId,
                        permissionSettings =
                            EmployeePermission(
                                roles =
                                    Json.decodeFromString<List<RoleRow>>(row.asString("roles")).map {
                                        EmployeeRole(it.id, NonBlankName.from(it.name), it.permissions)
                                    },
                                grantedPermissions = Json.decodeFromString<Set<UserPermission>>(row.asString("granted")),
                                revokedPermissions = Json.decodeFromString<Set<UserPermission>>(row.asString("revoked")),
                            ),
                        availableBranches =
                            if (row.asBoolean("all_branches_access")) {
                                EmployeeBranchAccess.All
                            } else {
                                EmployeeBranchAccess.Selected(Json.decodeFromString<List<BranchId>>(row.asString("branch_ids")))
                            },
                    )
                }
        if (result.size != distinctIds.size) {
            raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
        }
        return result
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun activeIdsWithAccessTo(branchId: BranchId): List<EmployeeId> =
        tr
            .sql(
                """
                SELECT e.id
                FROM employees e
                WHERE e.org_id = :orgId
                  AND e.is_active
                  AND (
                      e.all_branches_access
                      OR EXISTS (
                          SELECT 1 FROM employee_branches eb
                          WHERE eb.employee_id = e.id AND eb.branch_id = :branchId
                      )
                  )
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("branchId", branchId)
            .list { row -> row.asUuid("id").toEmployeeId() }

    private companion object {
        /**
         * Сотрудники организации с ролями, правами, выдачами/отзывами и филиалами одним запросом.
         * Коллекции — независимые коррелированные агрегаты, чтобы не перемножать строки.
         */
        val BY_IDS_SQL =
            """
            SELECT
                e.id, e.user_id, e.name, e.avatar_id, e.is_owner, e.is_active, e.joined_at,
                e.phone_no, e.email, e.all_branches_access,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                            'id', r.id,
                            'name', r.name,
                            'permissions', (SELECT COALESCE(jsonb_agg(rp.permission_key), '[]'::jsonb)
                                              FROM role_permissions rp
                                             WHERE rp.role_id = r.id))), '[]'::jsonb)
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
            WHERE e.org_id = :orgId AND e.id = ANY(:ids)
            """.trimIndent()
    }
}
