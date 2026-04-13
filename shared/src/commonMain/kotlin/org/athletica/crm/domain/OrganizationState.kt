package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.right
import arrow.optics.optics
import kotlinx.collections.immutable.PersistentList
import kotlinx.serialization.Serializable
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.commands.OrgCommand
import org.athletica.crm.domain.commands.UpdateOrgSettings

@optics
@Serializable
data class OrganizationState(
    val id: OrgId,
    val version: Long,
    val name: String,
    val timezone: String,
    val clients: PersistentList<Client>,
    val employees: PersistentList<Employee>,
    val groups: PersistentList<Group>,
    val disciplines: PersistentList<Discipline>,
) {
    companion object
}
