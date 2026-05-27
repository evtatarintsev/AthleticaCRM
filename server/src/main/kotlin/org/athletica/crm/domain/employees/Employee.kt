package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.UserActor
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

interface Employee {
    val id: EmployeeId
    val userId: UserId?
    val name: String
    val avatarId: UploadId?
    val isOwner: Boolean
    val isActive: Boolean
    val joinedAt: Instant
    val permissions: EmployeePermission
    val phoneNo: String?
    val email: EmailAddress?

    /** Доступ к филиалам организации: ко всем (включая будущие) либо к явному набору. */
    val availableBranches: EmployeeBranchAccess

    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun save()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun invite(email: EmailAddress, password: String)

    context(ctx: EmployeeRequestContext)
    fun withNew(
        newName: String,
        newPermissions: EmployeePermission,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
        newAvailableBranches: EmployeeBranchAccess,
    ): Employee
}

data class EmployeePermission(
    val roles: List<EmployeeRole>,
    val grantedPermissions: Set<UserPermission>,
    val revokedPermissions: Set<UserPermission>,
) : UserActor {
    constructor() : this(emptyList(), emptySet(), emptySet())

    override fun hasPermission(p: UserPermission): Boolean {
        if (p in revokedPermissions) return false
        if (p in grantedPermissions) return true
        return roles.any { p in it.permissions }
    }
}

/**
 * Доступ сотрудника к филиалам организации.
 * Делает невозможным некорректные комбинации `(allBranchesAccess: Boolean, branchIds: List)`:
 * - [All] — доступ ко всем филиалам организации, включая создаваемые в будущем.
 * - [Selected] — доступ только к перечисленным филиалам.
 */
sealed class EmployeeBranchAccess {
    /** Возвращает true, если у сотрудника есть доступ к филиалу [branchId]. */
    abstract fun contains(branchId: BranchId): Boolean

    object All : EmployeeBranchAccess() {
        override fun contains(branchId: BranchId): Boolean = true
    }

    data class Selected(val ids: List<BranchId>) : EmployeeBranchAccess() {
        override fun contains(branchId: BranchId): Boolean = branchId in ids
    }
}
