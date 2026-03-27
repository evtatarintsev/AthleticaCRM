package org.athletica.crm.core.auth

import kotlin.uuid.Uuid

/** Аутентифицированный пользователь системы. Реализуется разными доменными моделями (security, usecases). */
interface AuthenticatedUser {
    val id: Uuid
    val username: String
}
