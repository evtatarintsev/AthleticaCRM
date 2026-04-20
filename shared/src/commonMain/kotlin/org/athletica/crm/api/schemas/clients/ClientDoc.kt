package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.UploadId
import kotlin.time.Instant

/** Документ, прикреплённый к клиенту. */
@Serializable
data class ClientDoc(
    val id: ClientDocId,
    val uploadId: UploadId,
    val name: String,
    val createdAt: Instant,
)
