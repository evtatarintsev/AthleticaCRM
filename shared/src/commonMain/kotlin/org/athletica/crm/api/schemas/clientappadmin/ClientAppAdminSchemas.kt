package org.athletica.crm.api.schemas.clientappadmin

import kotlinx.serialization.Serializable
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import kotlin.time.Instant

/** Настройки клиентского кабинета организации. */
@Serializable
data class ClientAppSettingsResponse(
    /** Включён ли кабинет. */
    val enabled: Boolean,
    /** Есть ли у организации способ доставки кодов входа: включённый Telegram Gateway или SMS-канал. */
    val codeTransportAvailable: Boolean,
)

/** Включение или выключение кабинета. */
@Serializable
data class SetClientAppEnabledRequest(
    /** Включить ли кабинет. */
    val enabled: Boolean,
)

/** Выбор клиентов для массового приглашения. */
@Serializable
data class ClientAppBulkInviteRequest(
    /** Группы, участников которых пригласить; `null` — все активные клиенты организации. */
    val groupIds: List<GroupId>? = null,
)

/** Подготовка массового приглашения: сколько клиентов готовы и сколько без телефона. */
@Serializable
data class ClientAppInvitePreviewResponse(
    /** Активные клиенты, у которых есть ещё не приглашённый номер. */
    val ready: Int,
    /** Активные клиенты без телефона: пригласить их нельзя. */
    val withoutPhone: Int,
)

/** Итог массового приглашения. */
@Serializable
data class ClientAppBulkInviteResponse(
    /** Сколько клиентов приглашено. */
    val invited: Int,
    /** Ушли ли уведомления: `false` — у организации нет канала, который пишет на номер. */
    val notified: Boolean,
)

/** Запрос по клиенту. */
@Serializable
data class ClientAppClientRequest(
    /** Клиент. */
    val clientId: ClientId,
)

/** Запрос по номеру клиента. */
@Serializable
data class ClientAppPhoneRequest(
    /** Клиент. */
    val clientId: ClientId,
    /** Номер в виде для сравнения: только цифры. */
    val phone: String,
)

/** Итог приглашения клиента или напоминания. */
@Serializable
data class ClientAppInviteResponse(
    /** Ушли ли уведомления: `false` — канала нет, о кабинете клиенту нужно сообщить самому. */
    val notified: Boolean,
)

/** Блок «Клиентский кабинет» в карточке клиента. */
@Serializable
data class ClientAppClientAccessResponse(
    /** Включён ли кабинет в организации. */
    val enabled: Boolean,
    /** Телефоны клиента в порядке контактов. */
    val phones: List<ClientAppPhoneAccessSchema>,
)

/** Доступ к кабинету по одному номеру клиента. */
@Serializable
data class ClientAppPhoneAccessSchema(
    /** Номер в виде для сравнения: только цифры. */
    val phone: String,
    /** Статус доступа. */
    val status: ClientAppAccessStatus,
    /** Момент последнего уведомления о приглашении; `null` — номер не приглашали. */
    val invitedAt: Instant?,
    /** Канал последнего уведомления; `null` — уведомление не ушло или номер не приглашали. */
    val inviteChannel: String?,
    /** Момент последней смены статуса; `null` — номер не приглашали. */
    val statusChangedAt: Instant?,
    /** Последняя активность по профилю этой организации; `null` — активности не было. */
    val lastSeenAt: Instant?,
    /** С какого момента можно «Напомнить»; `null` — номер не в статусе «приглашён». */
    val remindAvailableAt: Instant?,
)
