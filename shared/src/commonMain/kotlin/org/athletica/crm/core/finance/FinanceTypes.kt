package org.athletica.crm.core.finance

import kotlinx.serialization.Serializable

/** Вид места хранения денег. */
@Serializable
enum class CashAccountKind { CASH, BANK, ACQUIRING, ONLINE }

/** Направление статьи ДДС. */
@Serializable
enum class CashDirection { INCOME, EXPENSE }

/** Вид денежного события главной книги. */
@Serializable
enum class LedgerKind { SALE, SALE_RETURN, PAYMENT, PAYOUT, ADJUSTMENT, INCOME, EXPENSE, TRANSFER, OPENING, REVERSAL }

/** Вид позиции продажи. */
@Serializable
enum class SaleItemKind { MEMBERSHIP, SERVICE }

/** Состояние оплаты продажи. */
@Serializable
enum class SalePaymentState { UNPAID, PARTIAL, PAID }
