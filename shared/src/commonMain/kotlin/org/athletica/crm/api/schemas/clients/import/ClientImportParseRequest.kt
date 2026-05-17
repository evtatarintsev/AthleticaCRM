package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.UploadId

/**
 * Запрос на разбор ранее загруженного CSV.
 * [uploadId] — идентификатор загрузки, полученный из `POST /api/upload`.
 */
@Serializable
data class ClientImportParseRequest(
    val uploadId: UploadId,
)
