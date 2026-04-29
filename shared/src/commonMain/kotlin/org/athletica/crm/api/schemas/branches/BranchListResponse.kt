package org.athletica.crm.api.schemas.branches

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.BranchId

@Serializable
data class BranchListResponse(
    val branches: List<BranchDetailResponse>,
)

@Serializable
data class BranchDetailResponse(
    val id: BranchId,
    val name: String,
)
