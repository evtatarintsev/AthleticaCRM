package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Actor
import org.athletica.crm.core.permissions.Permission
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

    context(ctx: RequestContext, tr: Transaction)
    suspend fun save()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun invite(email: EmailAddress, password: String)

    context(ctx: RequestContext)
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
    val grantedPermissions: Set<Permission>,
    val revokedPermissions: Set<Permission>,
) : Actor {
    constructor() : this(emptyList(), emptySet(), emptySet())

    override fun hasPermission(permission: Permission): Boolean {
        if (permission in revokedPermissions) return false
        if (permission in grantedPermissions) return true
        return roles.any { permission in it.permissions }
    }
}
