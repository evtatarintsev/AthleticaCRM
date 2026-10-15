package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.messaging.ReceiveInboundRequest
import org.athletica.crm.api.schemas.messaging.ReceiveInboundResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.InboundReceiptId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.inbound.InboundReceiptState
import org.athletica.crm.domain.inbound.InboundReceipts
import org.athletica.crm.storage.Transaction

/**
 * Принимает входящее из внешнего канала: пишет сырую запись в инбокс, затем пытается привязать её к
 * клиенту по связке адреса. Нашёлся клиент — сообщение попадает в его беседу (`appendInbound`) и
 * запись помечается разобранной; не нашёлся — остаётся в триаже для ручного разбора.
 *
 * Первая итерация — стенд-ин вебхука: вызывается в контексте сотрудника. Реальный вебхук (системный
 * контекст без сотрудника) появится вместе с реальными провайдерами.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun receiveInbound(
    request: ReceiveInboundRequest,
    bindings: ChannelBindings,
    receipts: InboundReceipts,
    conversations: Conversations,
): ReceiveInboundResponse {
    val receipt = receipts.record(request.integrationId, request.fromAddress, request.body)
    val clientId = bindings.clientFor(request.integrationId, request.fromAddress)
    if (clientId == null) {
        return ReceiveInboundResponse(resolvedClientId = null, receiptId = receipt.id)
    }
    val conversation = conversations.forClient(clientId)
    val message = conversation.appendInbound(request.integrationId, request.body)
    receipts.markResolved(receipt.id, message.id)
    return ReceiveInboundResponse(resolvedClientId = clientId, receiptId = receipt.id)
}

/**
 * Ручной разбор: привязывает входящее [receiptId] к клиенту [clientId]. Создаёт связку адреса (чтобы
 * следующие сообщения с этого адреса резолвились автоматически), кладёт сообщение в беседу клиента и
 * помечает запись разобранной.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun assignInbound(
    receiptId: InboundReceiptId,
    clientId: ClientId,
    bindings: ChannelBindings,
    receipts: InboundReceipts,
    conversations: Conversations,
) {
    val receipt = receipts.byId(receiptId) ?: raise(CommonDomainError("RECEIPT_NOT_FOUND", "Входящее не найдено"))
    ensure(receipt.state == InboundReceiptState.UNRESOLVED) {
        CommonDomainError("RECEIPT_ALREADY_RESOLVED", "Входящее уже разобрано")
    }
    bindings.link(receipt.integrationId, clientId, receipt.fromAddress)
    val conversation = conversations.forClient(clientId)
    val message = conversation.appendInbound(receipt.integrationId, receipt.body)
    receipts.markResolved(receipt.id, message.id)
}
