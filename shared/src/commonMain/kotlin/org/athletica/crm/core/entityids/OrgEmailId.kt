package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class OrgEmailId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = OrgEmailId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toOrgEmailId(): OrgEmailId = OrgEmailId(this)
