package org.athletica.crm.api.schemas.upload

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.UploadId

/** Запрос информации о загруженном файле по [id]. */
@Serializable
data class UploadInfoRequest(
    val id: UploadId,
)
