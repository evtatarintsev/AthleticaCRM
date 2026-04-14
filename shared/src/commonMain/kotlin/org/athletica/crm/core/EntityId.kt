package org.athletica.crm.core

import kotlin.uuid.Uuid

interface EntityId {
    val value: Uuid
}
