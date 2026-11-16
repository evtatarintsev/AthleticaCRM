package org.athletica.crm.api.schemas.org

import kotlinx.serialization.Serializable

/** Включаемые разделы карточки клиента организации. */
@Serializable
data class ClientCardSectionsResponse(
    val tasks: Boolean,
    val notes: Boolean,
    val chat: Boolean,
    val documents: Boolean,
)

/** Сохранение полного набора разделов; данные выключенных разделов сохраняются. */
@Serializable
data class UpdateClientCardSectionsRequest(
    val tasks: Boolean,
    val notes: Boolean,
    val chat: Boolean,
    val documents: Boolean,
)
