package org.athletica.crm.api.schemas.upload

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UploadResponse(
    val id: Uuid,
    val url: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)
