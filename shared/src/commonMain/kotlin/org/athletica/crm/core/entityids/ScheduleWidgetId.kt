package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор виджета расписания. */
@Serializable
@JvmInline
value class ScheduleWidgetId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ScheduleWidgetId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toScheduleWidgetId(): ScheduleWidgetId = ScheduleWidgetId(this)
