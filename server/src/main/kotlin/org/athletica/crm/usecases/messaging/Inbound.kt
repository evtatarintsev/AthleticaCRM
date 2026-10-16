package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.channels.ChannelChats
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.inbound.InboundReceipts
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** Хранилища, с которыми работают приём и разбор входящих связанных каналов. */
class InboundStores(
    /** Связки «адрес чата ↔ клиент». */
    val bindings: ChannelBindings,
    /** Сырые входящие (инбокс). */
    val receipts: InboundReceipts,
    /** Беседы с клиентами. */
    val conversations: Conversations,
    /** Сведения о чатах связанных каналов. */
    val chats: ChannelChats,
    /** Контакты клиентов — для автопривязки по номеру. */
    val contacts: ClientContacts,
)

/** Итог попытки автоматической привязки чата по присланному номеру. */
sealed interface PhoneLinkResult {
    /** Чат привязан к клиенту [clientId]. */
    data class Linked(val clientId: ClientId) : PhoneLinkResult

    /** Чат уже был привязан раньше — номер только сохранён. */
    data object AlreadyLinked : PhoneLinkResult

    /** Однозначного совпадения нет (ни одного или несколько клиентов) — чат ждёт ручного разбора. */
    data object NotMatched : PhoneLinkResult
}

/**
 * Принимает входящее [body] с адреса [fromAddress] интеграции [integrationId]: пишет сырую запись в
 * инбокс, затем ищет связку адреса. Чат привязан — сообщение попадает в беседу клиента, запись
 * помечается разобранной, а связка — последней активной. Не привязан — запись остаётся в очереди разбора.
 * Выполняется без сотрудника (системный контекст организации). Возвращает клиента или `null`.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun receiveInbound(
    integrationId: ChannelIntegrationId,
    fromAddress: String,
    body: String,
    stores: InboundStores,
): ClientId? {
    val receipt = stores.receipts.record(integrationId, fromAddress, body)
    val clientId = stores.bindings.clientFor(integrationId, fromAddress) ?: return null
    val conversation = stores.conversations.forClient(clientId)
    val message = conversation.appendInbound(integrationId, receipt.body, receipt.receivedAt)
    conversation.touch()
    stores.receipts.markResolved(receipt.id, message.id)
    stores.bindings.touchInbound(integrationId, fromAddress)
    return clientId
}

/**
 * Привязывает чат [address] интеграции [integrationId] к клиенту [clientId] и разбирает накопленное:
 * все неразобранные входящие чата в порядке получения переносятся в беседу клиента и помечаются
 * разобранными. Общая часть ручного разбора и автопривязки по номеру.
 * `CHAT_ALREADY_LINKED`, если чат уже привязан; `NOT_FOUND`, если клиента нет в организации.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun linkChat(
    integrationId: ChannelIntegrationId,
    address: String,
    clientId: ClientId,
    stores: InboundStores,
) {
    stores.bindings.link(integrationId, clientId, address)
    val pending = stores.receipts.unresolvedFrom(integrationId, address)
    if (pending.isEmpty()) {
        return
    }
    val conversation = stores.conversations.forClient(clientId)
    pending.forEach { receipt ->
        val message = conversation.appendInbound(integrationId, receipt.body, receipt.receivedAt)
        stores.receipts.markResolved(receipt.id, message.id)
    }
    conversation.touch()
    stores.bindings.touchInbound(integrationId, address)
}

/**
 * Автопривязка по номеру [rawPhone], который чат [address] прислал о себе. Номер сохраняется в чате
 * для ручного разбора; если он без учёта форматирования совпал ровно с одним клиентом организации и
 * чат ещё не привязан — чат привязывается к этому клиенту вместе с накопленными сообщениями.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun linkChatByPhone(
    integrationId: ChannelIntegrationId,
    address: String,
    rawPhone: String,
    stores: InboundStores,
): PhoneLinkResult {
    if (stores.bindings.clientFor(integrationId, address) != null) {
        return PhoneLinkResult.AlreadyLinked
    }
    val phone = PhoneDigits.from(rawPhone) ?: return PhoneLinkResult.NotMatched
    val candidate = stores.contacts.clientsByPhone(phone).singleOrNull() ?: return PhoneLinkResult.NotMatched
    linkChat(integrationId, address, candidate, stores)
    return PhoneLinkResult.Linked(candidate)
}

/**
 * Ручной разбор: сотрудник привязывает чат [address] интеграции [integrationId] к клиенту [clientId].
 * `NOT_FOUND`, если чата или клиента нет в организации; `CHAT_ALREADY_LINKED`, если чат уже привязан,
 * в том числе параллельным запросом.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun assignChat(
    integrationId: ChannelIntegrationId,
    address: String,
    clientId: ClientId,
    stores: InboundStores,
) {
    val chatKnown =
        stores.chats.byAddress(integrationId, address) != null ||
            stores.receipts.unresolvedFrom(integrationId, address).isNotEmpty() ||
            stores.bindings.clientFor(integrationId, address) != null
    ensure(chatKnown) { CommonDomainError("NOT_FOUND", Messages.ChatNotFound.localize()) }
    linkChat(integrationId, address, clientId, stores)
}
