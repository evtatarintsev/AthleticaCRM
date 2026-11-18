package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/** Поле, по которому сортируется список клиентов на сервере. */
@Serializable
enum class ClientSortField {
    /** По имени клиента. */
    NAME,

    /** По балансу личного счёта. */
    BALANCE,

    /** По дате рождения. */
    BIRTHDAY,

    /** По месяцу и дню относительно начала фильтра дней рождения, без учёта года рождения. */
    NEXT_BIRTHDAY,
}
