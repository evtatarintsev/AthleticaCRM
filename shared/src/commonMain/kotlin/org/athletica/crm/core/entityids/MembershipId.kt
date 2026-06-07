package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Идентификатор выданного абонемента (экземпляра, не тарифа-шаблона).
 */
@Serializable
@JvmInline
value class MembershipId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = MembershipId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toMembershipId(): MembershipId = MembershipId(this)
