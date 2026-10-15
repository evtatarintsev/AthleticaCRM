package org.athletica.infra.messaging.twilio

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.storage.Transaction
import org.athletica.infra.messaging.contactAddress
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * SMS-канал через Twilio (прямой: адрес — телефонный контакт клиента).
 * Первая итерация — заглушка: ничего реально не отправляет, только логирует.
 */
class TwilioSmsChannel(
    override val id: ChannelIntegrationId,
    override val name: String,
    private val settings: ChannelConfig.TwilioSms,
    override val enabled: Boolean,
    private val contacts: ClientContacts,
) : ChannelIntegration {
    private val logger = LoggerFactory.getLogger(TwilioSmsChannel::class.java)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun recipientAddress(clientId: ClientId, contactId: ClientContactId?): String = contactAddress(clientId, contactId, settings.acceptedContactTypes, contacts)

    override suspend fun send(address: String, body: String): SendResult {
        logger.info("STUB[twilio_sms from={}] -> {}: {}", settings.from, address, body)
        return SendResult.Sent("stub-${Uuid.generateV7()}")
    }
}
