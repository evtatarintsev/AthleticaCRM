package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId

@Serializable
data class DeleteClientDocRequest(
    val clientId: ClientId,
    val docId: ClientDocId,
)
