package org.athletica.crm.core.messaging

/**
 * Тип канала связи, по которому отправляются сообщения клиентам.
 *
 * Флаги возможностей ([supportsInbound], [supportsReceipts]) описывают, что канал умеет:
 * UI и логика опираются на них, а не на хардкод по конкретному типу. На первой итерации
 * реальные адаптеры отсутствуют (используется stub), поэтому флаги отражают целевые возможности.
 */
enum class ChannelType(
    /** Канал умеет принимать входящие сообщения (webhook / long-poll). */
    val supportsInbound: Boolean,
    /** Канал присылает квитанции о доставке/прочтении (delivery receipt). */
    val supportsReceipts: Boolean,
) {
    SMS(supportsInbound = false, supportsReceipts = true),
    TELEGRAM(supportsInbound = true, supportsReceipts = true),
    WHATSAPP(supportsInbound = true, supportsReceipts = true),
    EMAIL(supportsInbound = true, supportsReceipts = false),
    MAX(supportsInbound = true, supportsReceipts = true),
    VK(supportsInbound = true, supportsReceipts = true),

    /** Личный кабинет клиента: адрес не нужен, всегда доступен. */
    IN_APP(supportsInbound = true, supportsReceipts = true),
}
