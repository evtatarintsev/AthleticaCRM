package org.athletica.crm.domain

import arrow.core.Either
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.commands.OrgCommand

interface OrgStore {
    suspend fun currentState(): OrganizationState

    suspend fun apply(command: OrgCommand): Either<DomainError, OrganizationState>

    suspend fun commandsSince(version: Long): List<OrgCommand>
}
