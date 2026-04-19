package org.athletica.crm.core.auth

import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId

/** Аутентифицированный пользователь системы. Реализуется разными доменными моделями (security, usecases). */
interface AuthenticatedUser {
    val id: UserId
    val orgId: OrgId
    val username: String
}
