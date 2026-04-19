package org.athletica.crm.api.schemas.upload

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.UploadId

@Serializable
data class UploadResponse(
    val id: UploadId,
    val url: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)
