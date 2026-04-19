package org.athletica.crm.domain

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeRole
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
    override val roles: List<EmployeeRole>,
    override val phoneNo: String?,
    override val email: EmailAddress?,
) : Employee {
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun save() {
    }

    context(ctx: RequestContext, tr: Transaction, raise: arrow.core.raise.Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) {
    }
}

class EmployeesStub(employees: List<EmployeeStub>, private val clock: Clock) : Employees {
    val employees: MutableList<EmployeeStub> = employees.toMutableList()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
    ): Employee =
        EmployeeStub(
            id = id,
            userId = ctx.userId,
            isOwner = false,
            isActive = false,
            joinedAt = clock.now(),
            roles = emptyList(),
            name = name,
            phoneNo = phoneNo,
            email = email,
            avatarId = avatarId,
        ).also { employees.add(it) }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId): Employee = employees.first { it.id == id }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Employee> = employees
}
