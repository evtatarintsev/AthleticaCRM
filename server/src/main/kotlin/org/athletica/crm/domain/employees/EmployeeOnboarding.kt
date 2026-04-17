package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction
import org.athletica.crm.domain.auth.Users
import org.athletica.infra.mail.Mailbox

class EmployeeOnboarding(
    private val employees: Employees,
    private val users: Users,
    private val mailbox: Mailbox,
) {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun register(id: EmployeeId, email: String, password: String) {
        employees.byId(id)
    }
}
