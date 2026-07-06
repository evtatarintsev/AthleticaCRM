package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Строго типизированный идентификатор уведомления ([org.athletica.crm.domain.notifications.Notification]). */
@Serializable
@JvmInline
value class NotificationId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = NotificationId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Оборачивает [Uuid] в [NotificationId]. */
fun Uuid.toNotificationId(): NotificationId = NotificationId(this)
