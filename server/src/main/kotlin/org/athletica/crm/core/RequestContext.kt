package org.athletica.crm.core

import kotlin.uuid.Uuid

@JvmInline
value class UserId(val value: Uuid)

@JvmInline
value class OrgId(val value: Uuid)

data class RequestContext(
    val lang: Lang,
    val userId: UserId,
    val orgId: OrgId,
)
