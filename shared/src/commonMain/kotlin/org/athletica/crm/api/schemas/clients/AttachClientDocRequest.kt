package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.UploadId

/** Запрос на прикрепление загруженного файла к клиенту как документа. */
@Serializable
data class AttachClientDocRequest(
    val clientId: ClientId,
    val uploadId: UploadId,
    val name: String,
)
