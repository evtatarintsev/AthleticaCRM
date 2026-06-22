package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор экземпляра виджета на главной странице. */
@Serializable
@JvmInline
value class DashboardWidgetId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = DashboardWidgetId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Создаёт [DashboardWidgetId] из [Uuid]. */
fun Uuid.toDashboardWidgetId(): DashboardWidgetId = DashboardWidgetId(this)
