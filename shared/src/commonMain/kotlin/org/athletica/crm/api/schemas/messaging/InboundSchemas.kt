package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.InboundReceiptId

/**
 * Приём входящего из внешнего канала. На первой итерации вызывается вручную (стенд-ин вебхука):
 * реальные провайдеры будут парсить свой payload и вызывать этот же путь.
 * [fromAddress] — адрес отправителя в адресном пространстве канала (chat_id/телефон/…).
 */
@Serializable
data class ReceiveInboundRequest(
    val integrationId: ChannelIntegrationId,
    val fromAddress: String,
    val body: String,
)

/** Итог приёма входящего: привязано к беседе автоматически либо отправлено на ручной разбор. */
@Serializable
data class ReceiveInboundResponse(
    /** Клиент, к беседе которого привязано сообщение; `null` — попало в триаж. */
    val resolvedClientId: ClientId?,
    /** Идентификатор записи инбокса (для триажа, когда не привязано). */
    val receiptId: InboundReceiptId,
)

/** Неразобранное входящее в очереди триажа. */
@Serializable
data class InboundReceiptSchema(
    val id: InboundReceiptId,
    val integrationId: ChannelIntegrationId,
    val fromAddress: String,
    val body: String,
    /** Момент поступления в формате ISO-8601 (UTC). */
    val receivedAt: String,
)

/** Очередь ручного разбора входящих. */
@Serializable
data class TriageListResponse(
    val receipts: List<InboundReceiptSchema>,
)

/** Ручная привязка входящего [receiptId] к клиенту [clientId] (создаёт связку и разбирает запись). */
@Serializable
data class AssignInboundRequest(
    val receiptId: InboundReceiptId,
    val clientId: ClientId,
)
