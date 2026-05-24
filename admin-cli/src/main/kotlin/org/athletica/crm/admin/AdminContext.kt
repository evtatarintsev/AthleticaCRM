package org.athletica.crm.admin

import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import kotlin.uuid.Uuid

private val ADMIN_UUID = Uuid.fromLongs(0L, 0L)

/**
 * Создаёт [RequestContext] для административных операций из CLI.
 * Не привязан к реальному пользователю или сотруднику.
 * [orgId] — идентификатор целевой организации.
 * [currency] — валюта организации, определяет единицу для [Money]-операций.
 */
fun adminContext(
    orgId: OrgId,
    currency: Currency,
): RequestContext =
    RequestContext(
        lang = Lang.RU,
        userId = UserId(ADMIN_UUID),
        orgId = orgId,
        branchId = BranchId(ADMIN_UUID),
        employeeId = EmployeeId(ADMIN_UUID),
        username = "admin-cli",
        clientIp = null,
        currency = currency,
        permission = EmployeePermission(),
    )
