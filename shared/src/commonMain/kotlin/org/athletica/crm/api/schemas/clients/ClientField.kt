package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/**
 * Каталог доступных стандартных полей клиента, которые можно показать в таблице
 * списка клиентов или включить в экспорт. Имя клиента в каталоге не присутствует —
 * оно отображается и экспортируется всегда. Кастомные поля каталогом не покрываются,
 * они грузятся отдельно через `/api/custom-fields/list?entityType=CLIENT`.
 */
@Serializable
enum class ClientField(
    /** Стабильный ключ для сохранения в настройках отображения и в запросах экспорта. */
    val apiKey: String,
) {
    BIRTHDAY("birthday"),
    GENDER("gender"),
    GROUPS("groups"),
    BALANCE("balance"),
    ;

    companion object {
        /** Возвращает значение перечисления по [apiKey], либо null если такого поля нет. */
        fun byKey(apiKey: String): ClientField? = entries.firstOrNull { it.apiKey == apiKey }
    }
}
