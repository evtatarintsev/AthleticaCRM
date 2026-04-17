package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction

class EmployeeOnboarding(private val employees: Employees) {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun register(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: String?,
        avatarId: UploadId?,
    ): Employee = employees.new(id, name, phoneNo, email, avatarId)
}
