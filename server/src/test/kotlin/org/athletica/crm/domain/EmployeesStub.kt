package org.athletica.crm.domain

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

data class EmployeeStub(
    override val id: EmployeeId,
    override val userId: UserId?,
    override val name: String,
    override val avatarId: UploadId?,
    override val isOwner: Boolean,
    override val isActive: Boolean,
    override val joinedAt: Instant,
    override val permissions: EmployeePermission,
    override val phoneNo: String?,
    override val email: EmailAddress?,
    override val availableBranches: EmployeeBranchAccess = EmployeeBranchAccess.All,
) : Employee {
    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun save() {
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: arrow.core.raise.Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) {
    }

    context(ctx: EmployeeRequestContext)
    override fun withNew(
        newName: String,
        newPermissions: EmployeePermission,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
        newAvailableBranches: EmployeeBranchAccess,
    ) = copy(
        name = newName,
        permissions = newPermissions,
        email = newEmail,
        avatarId = newAvatarId,
        phoneNo = newPhoneNo,
        availableBranches = newAvailableBranches,
    )
}

class EmployeesStub(employees: List<EmployeeStub>, private val clock: Clock) : Employees {
    val employees: MutableList<EmployeeStub> = employees.toMutableList()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
        permissions: EmployeePermission,
        availableBranches: EmployeeBranchAccess,
    ): Employee =
        EmployeeStub(
            id = id,
            userId = ctx.userId,
            isOwner = false,
            isActive = false,
            joinedAt = clock.now(),
            name = name,
            phoneNo = phoneNo,
            email = email,
            avatarId = avatarId,
            permissions = EmployeePermission(emptyList(), emptySet(), emptySet()),
            availableBranches = availableBranches,
        ).also { employees.add(it) }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId): Employee = employees.first { it.id == id }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<EmployeeId>): List<Employee> = employees.filter { it.id in ids }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Employee> = employees
}
