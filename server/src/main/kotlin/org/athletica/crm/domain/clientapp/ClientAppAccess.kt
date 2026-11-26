package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientAppAccessId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Доступ к кабинету клиента по паре «клиент [clientId] организации [orgId] — номер [phone]».
 * Статус выражен типом: у каждого варианта есть только допустимые из него переходы,
 * поэтому, например, подключение неприглашённого номера невыразимо.
 * Переход записывается в БД и возвращает доступ в новом статусе; если статус успел
 * измениться в другом запросе — ошибка `CLIENT_APP_ACCESS_CHANGED`.
 */
sealed interface ClientAppAccess {
    /** Организация клиента. */
    val orgId: OrgId

    /** Клиент, к данным которого даёт доступ номер. */
    val clientId: ClientId

    /** Номер телефона в виде для сравнения. */
    val phone: PhoneDigits

    /** Статус доступа. */
    val status: ClientAppAccessStatus
}

/** Доступ, у которого уже было приглашение: он хранится в БД. */
sealed interface StoredClientAppAccess :
    ClientAppAccess,
    ClientAppAccessRecord

/** Данные хранимого доступа, общие для всех его статусов. */
interface ClientAppAccessRecord {
    /** Организация клиента. */
    val orgId: OrgId

    /** Клиент, к данным которого даёт доступ номер. */
    val clientId: ClientId

    /** Номер телефона в виде для сравнения. */
    val phone: PhoneDigits

    /** Идентификатор доступа. */
    val id: ClientAppAccessId

    /** Момент последнего уведомления о приглашении: самого приглашения или напоминания. */
    val invitedAt: Instant

    /** Название канала, которым ушло последнее уведомление; `null` — уведомление не отправлялось. */
    val inviteChannel: String?

    /** Момент последней смены статуса. */
    val statusChangedAt: Instant

    /** Последняя активность по профилю этой организации; `null` — активности не было. */
    val lastSeenAt: Instant?
}

/** Номер записан у клиента, но не приглашён. Строки в БД нет. */
interface NotInvitedClientAppAccess : ClientAppAccess {
    override val status: ClientAppAccessStatus get() = ClientAppAccessStatus.NOT_INVITED

    /** Приглашает номер; [channel] — название канала, которым ушло уведомление, `null` — не ушло. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun invite(channel: String? = null): InvitedClientAppAccess
}

/** Номер приглашён, владелец ещё не подключился. */
interface InvitedClientAppAccess : StoredClientAppAccess {
    override val status: ClientAppAccessStatus get() = ClientAppAccessStatus.INVITED

    /** Подключает владельца номера: он начинает видеть данные клиента. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun connect(): ConnectedClientAppAccess

    /** Отмечает, что владелец номера отказался от приглашения: «Это не я». */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun rejectAsNotMe(): NotMeClientAppAccess

    /** Отключает доступ. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun disable(): DisabledClientAppAccess

    /** Момент, с которого можно напомнить о приглашении: через сутки после прошлого уведомления. */
    val remindAvailableAt: Instant get() = invitedAt + REMIND_INTERVAL

    /**
     * Отмечает напоминание о приглашении каналом [channel] (`null` — уведомление не ушло).
     * Раньше [remindAvailableAt] — ошибка `CLIENT_APP_REMIND_TOO_SOON`.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun remind(channel: String?): InvitedClientAppAccess

    /** Правила напоминаний. */
    companion object {
        /** Не чаще одного напоминания за этот интервал. */
        val REMIND_INTERVAL = 1.days
    }
}

/** Владелец номера подключён и видит данные клиента. */
interface ConnectedClientAppAccess : StoredClientAppAccess {
    override val status: ClientAppAccessStatus get() = ClientAppAccessStatus.CONNECTED

    /** Отключает доступ. Сеансы аккаунта номера не завершаются. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun disable(): DisabledClientAppAccess
}

/** Владелец номера сообщил, что приглашение не для него. Пригласить снова можно только из карточки клиента. */
interface NotMeClientAppAccess : StoredClientAppAccess {
    override val status: ClientAppAccessStatus get() = ClientAppAccessStatus.NOT_ME

    /** Приглашает номер снова; [channel] — название канала, которым ушло уведомление, `null` — не ушло. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun invite(channel: String? = null): InvitedClientAppAccess
}

/** Доступ отключён. Пригласить снова можно только из карточки клиента. */
interface DisabledClientAppAccess : StoredClientAppAccess {
    override val status: ClientAppAccessStatus get() = ClientAppAccessStatus.DISABLED

    /** Приглашает номер снова; [channel] — название канала, которым ушло уведомление, `null` — не ушло. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun invite(channel: String? = null): InvitedClientAppAccess
}
