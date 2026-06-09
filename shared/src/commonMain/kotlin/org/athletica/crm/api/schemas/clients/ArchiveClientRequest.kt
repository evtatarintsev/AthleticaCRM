package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/** Запрос на архивирование клиентов по их идентификаторам. */
@Serializable
data class ArchiveClientRequest(
    /** Идентификаторы клиентов для отправки в архив. */
    val clientIds: List<ClientId>,
)

/** Запрос на восстановление клиентов из архива по их идентификаторам. */
@Serializable
data class RestoreClientRequest(
    /** Идентификаторы клиентов для восстановления. */
    val clientIds: List<ClientId>,
)
