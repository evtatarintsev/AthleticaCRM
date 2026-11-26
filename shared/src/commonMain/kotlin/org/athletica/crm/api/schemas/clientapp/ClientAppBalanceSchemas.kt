package org.athletica.crm.api.schemas.clientapp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Money

/** Движение по балансу клиента [id]: дата [occurredOn], тип операции [kind] и сумма со знаком [amount]. */
@Serializable
data class ClientAppBalanceEntrySchema(
    val id: LedgerTransactionId,
    val occurredOn: LocalDate,
    val kind: LedgerKind,
    val amount: Money,
)

/**
 * Баланс клиента [balance] в валюте организации, число неоплаченных занятий [unpaidSessions]
 * и движения [entries] от новых к старым. Примечаний операций нет.
 */
@Serializable
data class ClientAppBalanceResponse(
    val balance: Money,
    val unpaidSessions: Int,
    val entries: List<ClientAppBalanceEntrySchema>,
)
