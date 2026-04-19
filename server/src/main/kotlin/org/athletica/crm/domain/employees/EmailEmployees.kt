package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.storage.Transaction

class EmailEmployees(private val delegate: Employees, private val orgEmails: OrgEmails) : Employees by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId) = EmailEmployee(delegate.byId(id), orgEmails)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { EmailEmployee(it, orgEmails) }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
    ) = EmailEmployee(delegate.new(id, name, phoneNo, email, avatarId), orgEmails)
}
