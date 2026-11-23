package org.athletica.crm.core.orgbalance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тип операции по балансу организации; [code] совпадает со значением enum `org_balance_operation_type` в БД. */
@Serializable
enum class OrgBalanceOperationType(val code: String) {
    /** Пополнение через платёжную систему. */
    @SerialName("replenishment")
    REPLENISHMENT("replenishment"),

    /** Начисление бонусов. */
    @SerialName("bonus")
    BONUS("bonus"),

    /** Списание за использование системы. */
    @SerialName("system_fee")
    SYSTEM_FEE("system_fee"),

    /** Административное пополнение. */
    @SerialName("admin_credit")
    ADMIN_CREDIT("admin_credit"),

    /** Административное списание. */
    @SerialName("admin_debit")
    ADMIN_DEBIT("admin_debit"),
}
