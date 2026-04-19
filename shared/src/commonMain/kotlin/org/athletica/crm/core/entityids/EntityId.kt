package org.athletica.crm.core.entityids

import kotlin.uuid.Uuid

interface EntityId {
    val value: Uuid
}
