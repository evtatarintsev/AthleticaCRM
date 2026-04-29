package org.athletica.crm.api.schemas.branches

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.BranchId

@Serializable
data class DeleteBranchRequest(
    val ids: List<BranchId>,
)
