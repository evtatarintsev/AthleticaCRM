package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.requireManageClientApp
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clientapp.ClientAppAccess
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.domain.clientapp.ClientAppConfig
import org.athletica.crm.domain.clientapp.ClientAppSettings
import org.athletica.crm.domain.clientapp.ConnectedClientAppAccess
import org.athletica.crm.domain.clientapp.DisabledClientAppAccess
import org.athletica.crm.domain.clientapp.InvitedClientAppAccess
import org.athletica.crm.domain.clientapp.NotInvitedClientAppAccess
import org.athletica.crm.domain.clientapp.NotMeClientAppAccess
import org.athletica.crm.domain.clientapp.StoredClientAppAccess
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.conversations.Author
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.domain.telegramgateway.TelegramGatewayIntegrations
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Управление клиентским кабинетом из CRM: включение, приглашения, напоминания и отключение доступов.
 * Уведомление о приглашении уходит первым включённым каналом организации, который пишет на номер телефона
 * ([ChannelIntegrations.enabledFor]), сообщением в переписке клиента; нет такого канала — приглашение всё равно
 * действует, а ответ сообщает, что уведомление не ушло. Изменения доступов пишутся в [audit].
 */
class ClientAppAdmin(
    private val settings: ClientAppSettings,
    private val accesses: ClientAppAccesses,
    private val gateway: TelegramGatewayIntegrations,
    private val channels: ChannelIntegrations,
    private val conversations: Conversations,
    private val deliveries: Deliveries,
    private val organizations: Organizations,
    private val config: ClientAppConfig,
    private val audit: AuditLog,
) {
    /** Есть ли у организации способ доставки кодов входа: включённый Telegram Gateway или канал, пишущий на номер. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun codeTransportAvailable(): Boolean = gateway.current()?.enabled == true || channels.enabledFor(ContactType.PHONE).isNotEmpty()

    /**
     * Включает или выключает кабинет; нужно право `CAN_MANAGE_CLIENT_APP`. Включить можно только при способе
     * доставки кодов ([codeTransportAvailable]), иначе `CLIENT_APP_NO_CODE_TRANSPORT`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun setEnabled(enabled: Boolean) {
        requireManageClientApp()
        if (enabled) {
            ensure(codeTransportAvailable()) {
                CommonDomainError("CLIENT_APP_NO_CODE_TRANSPORT", Messages.ClientAppNoCodeTransport.localize())
            }
        }
        settings.setEnabled(enabled)
    }

    /**
     * Приглашает клиента [clientId]: его номера в статусе «не приглашён», «это не я» и «отключён» становятся
     * приглашёнными, на них уходит уведомление. Возвращает, ушло ли уведомление. Кабинет выключен — `CLIENT_APP_DISABLED`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun invite(clientId: ClientId): Boolean {
        ensureEnabled()
        val channel = phoneChannel()
        invite(clientId, accesses.byClient(clientId), channel)
        return channel != null
    }

    /**
     * Приглашает активных клиентов организации ([groupIds] `null`) или участников групп [groupIds]: только номера
     * в статусе «не приглашён», поэтому повторный запуск не шлёт уведомлений уже приглашённым, а номера «это не я»
     * и «отключён» не трогает. Нужно право `CAN_MANAGE_CLIENT_APP`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun bulkInvite(groupIds: List<GroupId>?): BulkInvite {
        requireManageClientApp()
        ensureEnabled()
        val channel = phoneChannel()
        val byClient = accesses.notInvited(groupIds).groupBy { it.clientId }
        byClient.forEach { (clientId, phones) -> invite(clientId, phones, channel) }
        return BulkInvite(byClient.size, channel != null)
    }

    /**
     * «Напомнить»: повторно уведомляет приглашённый номер [phone] клиента [clientId] — не чаще раза в сутки
     * (`CLIENT_APP_REMIND_TOO_SOON`). Возвращает, ушло ли уведомление.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun remind(clientId: ClientId, phone: PhoneDigits): Boolean {
        ensureEnabled()
        val access = access(clientId, phone) as? InvitedClientAppAccess ?: raise(actionUnavailable())
        val channel = phoneChannel()
        val reminded = access.remind(channel?.name)
        logged(reminded, channel)
        channel?.let { notify(clientId, listOf(phone), it) }
        return channel != null
    }

    /**
     * «Отключить»: доступ номера [phone] клиента [clientId] в статусе «приглашён» или «подключён» становится
     * отключённым. Сеансы аккаунта номера и его доступы в других организациях не меняются.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun disable(clientId: ClientId, phone: PhoneDigits) {
        val disabled =
            when (val access = access(clientId, phone)) {
                is InvitedClientAppAccess -> access.disable()
                is ConnectedClientAppAccess -> access.disable()
                else -> raise(actionUnavailable())
            }
        logged(disabled, null)
    }

    /** Переводит в «приглашён» подходящие доступы [phones] клиента [clientId] и шлёт им одно уведомление каналом [channel]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun invite(
        clientId: ClientId,
        phones: List<ClientAppAccess>,
        channel: ChannelIntegration?,
    ) {
        val invited =
            phones.mapNotNull {
                when (it) {
                    is NotInvitedClientAppAccess -> it.invite(channel?.name)
                    is NotMeClientAppAccess -> it.invite(channel?.name)
                    is DisabledClientAppAccess -> it.invite(channel?.name)
                    else -> null
                }
            }
        invited.forEach { logged(it, channel) }
        if (channel != null && invited.isNotEmpty()) {
            notify(clientId, invited.map { it.phone }, channel)
        }
    }

    /**
     * Уведомление о приглашении клиента [clientId] на номера [phones] каналом [channel]: одно системное сообщение в
     * переписке клиента и доставка на каждый номер. Текст — название организации и ссылка, без данных клиента.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun notify(
        clientId: ClientId,
        phones: List<PhoneDigits>,
        channel: ChannelIntegration,
    ) {
        val body = Messages.ClientAppInvitation.localize(organizations.current().name, config.publicUrl)
        val conversation = conversations.forClient(clientId)
        val message = conversation.appendOutbound(Author.System, body)
        phones.forEach { phone -> deliveries.create(message.id, channel.id) { "+${phone.value}" } }
        conversation.touch()
    }

    /** Записывает новый статус доступа [access] в аудит; [channel] — канал уведомления, если оно ушло. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun logged(
        access: StoredClientAppAccess,
        channel: ChannelIntegration?,
    ) {
        val notified = if (channel == null) "" else ""","notifiedVia":"${channel.id}""""
        audit.logUpdate(ENTITY, access.id, """{"status":"${access.status}","phone":"${access.phone.value}"$notified}""")
    }

    /** Доступ номера [phone] клиента [clientId]; номера у клиента нет — ошибка «действие недоступно». */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun access(
        clientId: ClientId,
        phone: PhoneDigits,
    ): ClientAppAccess = accesses.byClient(clientId).firstOrNull { it.phone == phone } ?: raise(actionUnavailable())

    /** Первый включённый канал организации, который пишет на номер телефона; `null` — такого нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun phoneChannel(): ChannelIntegration? = channels.enabledFor(ContactType.PHONE).firstOrNull()

    /** Кабинет выключен — `CLIENT_APP_DISABLED`. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun ensureEnabled() {
        ensure(settings.enabled()) { CommonDomainError("CLIENT_APP_DISABLED", Messages.ClientAppDisabled.localize()) }
    }

    /** Действие недоступно для номера: его нет у клиента или статус не тот. */
    context(ctx: EmployeeRequestContext)
    private fun actionUnavailable() = CommonDomainError("CLIENT_APP_ACCESS_CHANGED", Messages.ClientAppPhoneActionUnavailable.localize())

    private companion object {
        /** Тип сущности в аудите. */
        const val ENTITY = "client_app_access"
    }
}

/** Итог массового приглашения: приглашено [invited] клиентов, [notified] — ушли ли уведомления. */
data class BulkInvite(
    val invited: Int,
    val notified: Boolean,
)
