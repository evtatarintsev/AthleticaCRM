package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId

/** Запрос на прикрепление загруженного файла к клиенту как документа. */
@Serializable
data class AttachClientDocRequest(
    val docId: ClientDocId,
    val clientId: ClientId,
    val uploadId: UploadId,
    val name: String,
)
