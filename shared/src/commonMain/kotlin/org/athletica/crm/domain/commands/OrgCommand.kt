package org.athletica.crm.domain.commands

import kotlinx.serialization.Serializable

sealed interface OrgCommand {
    /**
     * Команда применима только к состоянию с такой же версией
     */
    val version: Long
}

/**
 * Команда изменения
 */
@Serializable
data class UpdateOrgSettings(
    override val version: Long,
    val name: String,
    val timezone: String,
) : OrgCommand
