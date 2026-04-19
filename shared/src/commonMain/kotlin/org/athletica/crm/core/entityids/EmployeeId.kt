package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class EmployeeId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = EmployeeId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toEmployeeId(): EmployeeId = EmployeeId(this)
