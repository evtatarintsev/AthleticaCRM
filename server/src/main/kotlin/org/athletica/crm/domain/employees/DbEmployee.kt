package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.auth.AuthSessions
import org.athletica.crm.domain.auth.Users
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

data class DbEmployee(
    override val id: EmployeeId,
    override val name: String,
    override val avatarId: UploadId?,
    override val isOwner: Boolean,
    override val access: EmployeeAccess,
    override val joinedAt: Instant,
    override val phoneNo: String?,
    override val email: EmailAddress?,
    override val availableBranches: EmployeeBranchAccess,
    private val permissionSettings: EmployeePermission,
    private val users: Users,
    private val roleCatalog: Roles,
    private val orgId: OrgId,
    private val sessions: AuthSessions,
) : Employee {
    override val roles: List<EmployeeRole> get() = permissionSettings.roles

    /**
     * Проверяет право [p]: владелец организации имеет все права независимо от ролей,
     * персональных выдач и отзывов; остальным права даёт настройка [permissionSettings].
     */
    override fun hasPermission(p: UserPermission): Boolean = isOwner || permissionSettings.hasPermission(p)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        val (allBranchesAccess, branchIds) =
            when (val access = availableBranches) {
                is EmployeeBranchAccess.All -> true to emptyList()
                is EmployeeBranchAccess.Selected -> false to resolveEmployeeBranchIds(access.ids)
            }

        val updated =
            tr.sql(
                """
                UPDATE employees
                SET user_id             = :userId,
                    name                = :name,
                    avatar_id           = :avatarId,
                    phone_no            = :phoneNo,
                    email               = :email,
                    all_branches_access = :allBranchesAccess
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("userId", userId)
                .bind("name", name)
                .bind("avatarId", avatarId)
                .bind("phoneNo", phoneNo)
                .bind("email", email)
                .bind("allBranchesAccess", allBranchesAccess)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        ensure(updated == 1L) { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }

        tr.sql("DELETE FROM employee_roles WHERE employee_id = :id")
            .bind("id", id)
            .execute()

        permissionSettings.roles.forEach { role ->
            tr.sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                .bind("employeeId", id)
                .bind("roleId", role.id)
                .execute()
        }

        tr.sql("DELETE FROM employee_permission_overrides WHERE employee_id = :id")
            .bind("id", id)
            .execute()

        permissionSettings.grantedPermissions.forEach { permission ->
            tr.sql(
                """
                INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted)
                VALUES (:employeeId, :key, true)
                """.trimIndent(),
            )
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }

        permissionSettings.revokedPermissions.forEach { permission ->
            tr.sql(
                """
                INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted)
                VALUES (:employeeId, :key, false)
                """.trimIndent(),
            )
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }

        tr.sql("DELETE FROM employee_branches WHERE employee_id = :id")
            .bind("id", id)
            .execute()

        branchIds.forEach { branchId ->
            tr.sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                .bind("employeeId", id)
                .bind("branchId", branchId)
                .execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun invite(email: EmailAddress, password: String) {
        when (access) {
            is EmployeeAccess.NoLogin -> {}
            is EmployeeAccess.Active -> raise(CommonDomainError("EMPLOYEE_ACCESS_ALREADY_SENT", Messages.EmployeeAccessAlreadySent.localize()))
            is EmployeeAccess.Blocked -> raise(CommonDomainError("EMPLOYEE_BLOCKED", Messages.EmployeeIsBlocked.localize()))
        }
        val user = users.new(email.value, password)
        copy(access = EmployeeAccess.Active(user.id), email = email).save()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun block() {
        ensureBlockable()
        if (access is EmployeeAccess.Blocked) {
            return
        }
        updateBlockedAt(Clock.System.now())
        userId?.let { sessions.deleteOthers(it, keep = null) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun terminateSession(sessionId: AuthSessionId) {
        ensureBlockable()
        sessions.delete(sessionId, userId ?: raise(CommonDomainError("AUTH_SESSION_NOT_FOUND", Messages.AuthSessionNotFound.localize())))
    }

    /** Проверяет, что сотрудник не владелец и не автор запроса: только такого можно блокировать и лишать сессий. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    private fun ensureBlockable() {
        ensure(!isOwner) { CommonDomainError("EMPLOYEE_OWNER_CANNOT_BE_BLOCKED", Messages.EmployeeOwnerCannotBeBlocked.localize()) }
        ensure(id != ctx.employee.id) { CommonDomainError("EMPLOYEE_CANNOT_BLOCK_SELF", Messages.EmployeeCannotBlockSelf.localize()) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun unblock() {
        if (access is EmployeeAccess.Blocked) {
            updateBlockedAt(null)
        }
    }

    /** Записывает момент блокировки [blockedAt]; `null` снимает блокировку. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun updateBlockedAt(blockedAt: Instant?) {
        val updated =
            tr.sql("UPDATE employees SET blocked_at = :blockedAt WHERE id = :id AND org_id = :orgId")
                .bind("blockedAt", blockedAt)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        ensure(updated == 1L) { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun withNew(
        newName: NonBlankName,
        newPermissions: EmployeePermissionSelection,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
        newAvailableBranches: EmployeeBranchAccess,
    ): Employee {
        ensure(ctx.orgId == orgId) { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
        return copy(
            name = newName.value,
            permissionSettings = roleCatalog.resolve(newPermissions),
            email = newEmail,
            avatarId = newAvatarId,
            phoneNo = newPhoneNo,
            availableBranches = newAvailableBranches,
        )
    }
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun resolveEmployeeBranchIds(ids: List<BranchId>): List<BranchId> = DbBranches().byIds(ids).map { it.id }
