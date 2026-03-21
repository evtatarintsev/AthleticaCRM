package org.athletica.crm.core.auth

import kotlin.uuid.Uuid

interface AuthenticatedUser {
    val id: Uuid
    val username: String
}
