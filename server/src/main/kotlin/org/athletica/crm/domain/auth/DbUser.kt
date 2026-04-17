package org.athletica.crm.domain.auth

import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId

class DbUser(
    override val id: UserId,
    override val orgId: OrgId,
    override val username: String,
) : User
