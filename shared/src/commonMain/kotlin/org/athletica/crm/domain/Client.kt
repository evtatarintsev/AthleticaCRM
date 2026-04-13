package org.athletica.crm.domain

import arrow.optics.optics
import kotlinx.collections.immutable.PersistentList
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.GroupId
import org.athletica.crm.core.Money
import org.athletica.crm.core.UploadId
import kotlin.time.Instant
import kotlin.uuid.Uuid

@optics
@Serializable
data class Client(
    val id: ClientId,
    val name: String,
    val avatarId: UploadId? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
    /** Пол клиента. */
    val gender: Gender,
    /** Группы в которых состоит клиент. */
    val groups: PersistentList<GroupId>,
    /** Баланс личного счёта клиента (отрицательный — задолженность). */
    val balance: ClientBalance,
) {
    companion object
}

@optics
/** Одна запись в журнале операций по балансу клиента. */
@Serializable
data class ClientBalanceJournalEntry(
    val id: Uuid,
    /** Изменение баланса: положительное — пополнение, отрицательное — списание. */
    val amount: Money,
    /** Баланс клиента после операции. */
    val balanceAfter: Money,
    /** Тип операции: admin_credit, admin_debit, sale_overpayment, sale_payment, refund. */
    val operationType: String,
    /** Комментарий к операции (обязателен для admin_credit / admin_debit). */
    val note: String?,
    /** Сотрудник, выполнивший операцию, либо null если данные удалены. */
    val performedBy: PerformedBy?,
    /** Время операции. */
    val createdAt: Instant,
) {
    companion object
}

@optics
@Serializable
data class ClientBalance(
    val history: PersistentList<ClientBalanceJournalEntry>,
    val value: Money,
) {
    companion object
}
