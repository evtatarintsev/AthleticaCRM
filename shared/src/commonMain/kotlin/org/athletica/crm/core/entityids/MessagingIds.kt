package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор контакта клиента в рамках конкретного типа канала. */
@Serializable
@JvmInline
value class ClientContactId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ClientContactId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Идентификатор настроенной интеграции канала связи организации. */
@Serializable
@JvmInline
value class ChannelIntegrationId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ChannelIntegrationId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Идентификатор диалога с клиентом. */
@Serializable
@JvmInline
value class ConversationId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ConversationId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Идентификатор сообщения в диалоге. */
@Serializable
@JvmInline
value class MessageId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = MessageId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toClientContactId(): ClientContactId = ClientContactId(this)

fun Uuid.toChannelIntegrationId(): ChannelIntegrationId = ChannelIntegrationId(this)

fun Uuid.toConversationId(): ConversationId = ConversationId(this)

fun Uuid.toMessageId(): MessageId = MessageId(this)
