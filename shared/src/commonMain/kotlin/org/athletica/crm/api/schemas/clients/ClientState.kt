package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/** Состояние клиента: активный либо архивный. */
@Serializable
enum class ClientState {
    /** Активный клиент — отображается в обычных списках, доступен для редактирования. */
    ACTIVE,

    /** Архивный клиент — скрыт из обычных списков, доступно только восстановление. */
    ARCHIVED,
}
