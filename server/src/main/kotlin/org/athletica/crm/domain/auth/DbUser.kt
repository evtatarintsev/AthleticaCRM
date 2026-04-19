package org.athletica.crm.domain.auth

import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId

class DbUser(
    override val id: UserId,
    override val orgId: OrgId,
    override val username: String,
) : User
