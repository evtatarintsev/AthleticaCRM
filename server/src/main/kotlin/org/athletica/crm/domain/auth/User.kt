package org.athletica.crm.domain.auth

import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId

interface User {
    val id: UserId
    val orgId: OrgId
    val username: String
}
