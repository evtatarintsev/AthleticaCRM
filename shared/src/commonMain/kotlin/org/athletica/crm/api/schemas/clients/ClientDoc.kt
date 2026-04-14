package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.UploadId
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Документ, прикреплённый к клиенту. */
@Serializable
data class ClientDoc(
    val id: Uuid,
    val uploadId: UploadId,
    val name: String,
    val createdAt: Instant,
)
