package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Запрос на прикрепление загруженного файла к клиенту как документа. */
@Serializable
data class AttachClientDocRequest(
    val clientId: Uuid,
    val uploadId: Uuid,
    val name: String,
)
