package org.athletica.crm.domain.inbound

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.InboundReceiptId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Состояние сырого входящего: ещё не привязано к беседе либо уже разобрано. */
enum class InboundReceiptState {
    /** Не привязано к клиенту — ждёт автоматического или ручного разбора. */
    UNRESOLVED,

    /** Привязано к беседе: породило входящее сообщение [InboundReceipt.resolvedMessageId]. */
    RESOLVED,
}

/**
 * Сырое входящее сообщение из внешнего канала до разбора (инбокс).
 * [fromAddress] — адрес отправителя в адресном пространстве канала (chat_id/телефон/…).
 */
data class InboundReceipt(
    val id: InboundReceiptId,
    val integrationId: ChannelIntegrationId,
    val fromAddress: String,
    val body: String,
    val state: InboundReceiptState,
    val resolvedMessageId: MessageId?,
    val receivedAt: Instant,
)

/** Хранилище сырых входящих (инбокс). Изолировано по организации. */
interface InboundReceipts {
    /** Записывает входящее в статусе `UNRESOLVED` и возвращает его. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun record(integrationId: ChannelIntegrationId, fromAddress: String, body: String): InboundReceipt

    /**
     * Неразобранные входящие с адреса [fromAddress] интеграции [integrationId] в порядке получения.
     * Строки блокируются до конца транзакции, чтобы параллельный разбор не перенёс их дважды.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unresolvedFrom(integrationId: ChannelIntegrationId, fromAddress: String): List<InboundReceipt>

    /**
     * Помечает неразобранное входящее [id] разобранным, связывая его с порождённым сообщением [messageId].
     * `RECEIPT_ALREADY_RESOLVED`, если запись уже разобрана (в том числе параллельным запросом):
     * переход выполняется условным UPDATE, поэтому из двух одновременных разборов проходит один.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markResolved(id: InboundReceiptId, messageId: MessageId)
}
