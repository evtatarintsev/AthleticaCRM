package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор метки посещаемости из справочника организации. */
@Serializable
@JvmInline
value class AttendanceLabelId(override val value: Uuid) : EntityId {
    companion object {
        /** Новый идентификатор метки. */
        fun new() = AttendanceLabelId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Преобразует [Uuid] в [AttendanceLabelId]. */
fun Uuid.toAttendanceLabelId(): AttendanceLabelId = AttendanceLabelId(this)
