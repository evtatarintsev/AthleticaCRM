package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import kotlin.uuid.Uuid

private val SYSTEM_UUID = Uuid.fromLongs(0L, 0L)

/**
 * Создаёт [RequestContext] для системных фоновых операций (планировщики, воркеры).
 * Не привязан к реальному пользователю или сотруднику.
 * [branchId] — идентификатор целевого филиала; при org-wide операциях можно передать любой.
 * [currency] — валюта целевой организации; для фоновых задач, не работающих с деньгами,
 * допустимо передавать значение по умолчанию.
 */
fun systemContext(
    orgId: OrgId,
    branchId: BranchId = BranchId(SYSTEM_UUID),
    currency: Currency = Currency.RUB,
): RequestContext =
    RequestContext(
        lang = Lang.RU,
        userId = UserId(SYSTEM_UUID),
        orgId = orgId,
        branchId = branchId,
        employeeId = EmployeeId(SYSTEM_UUID),
        username = "system",
        clientIp = null,
        currency = currency,
        permission = EmployeePermission(),
    )
