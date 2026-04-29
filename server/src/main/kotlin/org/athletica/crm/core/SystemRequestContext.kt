package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.domain.employees.EmployeePermission
import kotlin.uuid.Uuid

private val SYSTEM_UUID = Uuid.fromLongs(0L, 0L)

/**
 * Создаёт [RequestContext] для системных фоновых операций (планировщики, воркеры).
 * Не привязан к реальному пользователю или сотруднику.
 * [branchId] — идентификатор целевого филиала; при org-wide операциях можно передать любой.
 */
fun systemContext(orgId: OrgId, branchId: BranchId = BranchId(SYSTEM_UUID)): RequestContext =
    RequestContext(
        lang = Lang.RU,
        userId = UserId(SYSTEM_UUID),
        orgId = orgId,
        branchId = branchId,
        employeeId = EmployeeId(SYSTEM_UUID),
        username = "system",
        clientIp = null,
        permission = EmployeePermission(),
    )
