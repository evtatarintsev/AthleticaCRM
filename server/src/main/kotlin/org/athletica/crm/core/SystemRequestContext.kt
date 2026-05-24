package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Currency

/**
 * Создаёт [SystemRequestContext] для системных фоновых операций (планировщики, воркеры, event handlers).
 * Не привязан к реальному пользователю или сотруднику.
 *
 * [orgId] — организация, в контексте которой выполняется операция.
 * [branchId] — целевой филиал; null для org-wide операций.
 * [currency] — валюта организации; для задач, не работающих с деньгами, допустимо передавать значение по умолчанию.
 */
fun systemContext(
    orgId: OrgId,
    branchId: BranchId? = null,
    currency: Currency = Currency.RUB,
): SystemRequestContext =
    SystemRequestContext(
        orgId = orgId,
        branchId = branchId,
        currency = currency,
    )
