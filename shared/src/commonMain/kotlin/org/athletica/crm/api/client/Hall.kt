package org.athletica.crm.api.client

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.halls.CreateHallRequest
import org.athletica.crm.api.schemas.halls.UpdateHallRequest
import org.athletica.crm.core.entityids.HallId

@Serializable
data class Hall(
    val id: HallId,
    val name: String,
)

fun Hall.toHallCreateRequest(): CreateHallRequest = CreateHallRequest(id = id, name = name)

fun Hall.toHallUpdateRequest(): UpdateHallRequest = UpdateHallRequest(id = id, name = name)
