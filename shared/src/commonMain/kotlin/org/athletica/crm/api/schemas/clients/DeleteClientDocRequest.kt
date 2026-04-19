package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import kotlin.uuid.Uuid

@Serializable
data class DeleteClientDocRequest(
    val clientId: ClientId,
    val docId: Uuid,
)
