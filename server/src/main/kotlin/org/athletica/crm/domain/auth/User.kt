package org.athletica.crm.domain.auth

import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId

interface User {
    val id: UserId
    val orgId: OrgId
    val username: String
}
