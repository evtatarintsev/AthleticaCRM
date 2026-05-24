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

    /** Доступ ко всем филиалам организации, включая будущие. */
    val allBranchesAccess: Boolean

    /** Конкретные филиалы; актуален только когда [allBranchesAccess] = false. */
    val branchIds: List<BranchId>

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
        newAllBranchesAccess: Boolean,
        newBranchIds: List<BranchId>,
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
