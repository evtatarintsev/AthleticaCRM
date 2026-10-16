package org.athletica.crm.domain.notifications

import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId

/** Объект, к которому относится уведомление; по нему клиент строит ссылку. */
sealed interface NotificationSubject {
    /** Уведомление ни к какому объекту не относится — ссылки нет. */
    data object None : NotificationSubject

    /** Группа [id]. */
    data class Group(val id: GroupId) : NotificationSubject

    /** Клиент [id]. */
    data class Client(val id: ClientId) : NotificationSubject
}
