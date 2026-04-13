package org.athletica.crm.domain

import arrow.core.Either
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Database
import org.athletica.crm.domain.commands.OrgCommand

class PostgresOrgStore(db: Database, ctx: RequestContext) : OrgStore {
    override suspend fun currentState(): OrganizationState = TODO("Not yet implemented")


    override suspend fun apply(command: OrgCommand) : Either<DomainError, OrganizationState> {

    }

    override suspend fun commandsSince(version: Long): List<OrgCommand> = TODO("Not yet implemented")
}
