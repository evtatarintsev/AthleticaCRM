package org.athletica.crm.api.client

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.branches.BranchCreateRequest
import org.athletica.crm.api.schemas.branches.BranchUpdateRequest
import org.athletica.crm.core.entityids.BranchId

@Serializable
data class Branch(
    val id: BranchId,
    val name: String,
)

fun Branch.toBranchCreateRequest(): BranchCreateRequest = BranchCreateRequest(id = id, name = name)

fun Branch.toBranchUpdateRequest(): BranchUpdateRequest = BranchUpdateRequest(id = id, name = name)
