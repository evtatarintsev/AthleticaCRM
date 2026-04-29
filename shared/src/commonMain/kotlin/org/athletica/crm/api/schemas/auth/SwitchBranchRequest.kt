package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.BranchId

/** Запрос на переключение активного филиала. */
@Serializable
data class SwitchBranchRequest(
    /** Идентификатор филиала, в который нужно переключиться. */
    val branchId: BranchId,
)
