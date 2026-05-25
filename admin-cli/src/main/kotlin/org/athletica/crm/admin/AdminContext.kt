package org.athletica.crm.admin

import org.athletica.crm.core.AdminRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import kotlin.uuid.Uuid

private val ADMIN_UUID = Uuid.fromLongs(0L, 0L)

/**
 * Создаёт [AdminRequestContext] для административных операций из CLI.
 * Не привязан к реальному пользователю или сотруднику.
 * [orgId] — идентификатор целевой организации.
 * [currency] — валюта организации, определяет единицу для [Money]-операций.
 */
fun adminContext(
    orgId: OrgId,
    currency: Currency,
): AdminRequestContext =
    AdminRequestContext(
        lang = Lang.RU,
        orgId = orgId,
        currency = currency,
        adminId = UserId(ADMIN_UUID),
        username = "admin-cli",
        clientIp = null,
    )
