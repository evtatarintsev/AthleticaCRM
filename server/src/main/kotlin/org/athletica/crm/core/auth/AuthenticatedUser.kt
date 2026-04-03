package org.athletica.crm.core.auth

import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId

/** Аутентифицированный пользователь системы. Реализуется разными доменными моделями (security, usecases). */
interface AuthenticatedUser {
    val id: UserId
    val orgId: OrgId
    val username: String
}
