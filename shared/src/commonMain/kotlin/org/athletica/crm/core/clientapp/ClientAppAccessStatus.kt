package org.athletica.crm.core.clientapp

import kotlinx.serialization.Serializable

/** Статус доступа к кабинету клиента по паре «клиент — номер телефона». */
@Serializable
enum class ClientAppAccessStatus {
    /** Номер записан у клиента, но не приглашён: доступа нет. */
    NOT_INVITED,

    /** Номер приглашён, владелец ещё не подключился. */
    INVITED,

    /** Владелец номера подключился и видит данные клиента. */
    CONNECTED,

    /** Владелец номера сообщил, что приглашение не для него. */
    NOT_ME,

    /** Доступ отключён сотрудником, удалением номера или удалением аккаунта. */
    DISABLED,
}
