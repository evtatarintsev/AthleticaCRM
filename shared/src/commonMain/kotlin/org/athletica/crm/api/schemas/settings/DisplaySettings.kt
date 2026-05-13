package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.ClientField

/** Глобальные настройки отображения данных в интерфейсе пользователя. */
@Serializable
data class DisplaySettings(
    /** Настройки таблицы клиентов. */
    val clients: ClientsDisplaySettings = ClientsDisplaySettings(),
)

/**
 * Настройки таблицы клиентов.
 * [columns] — упорядоченный список ключей видимых колонок. Ключи могут быть из
 * [ClientField] (стандартные поля) либо ключами кастомных полей клиента.
 * Колонка «Имя» в список не входит — она отображается всегда.
 */
@Serializable
data class ClientsDisplaySettings(
    val columns: List<String> =
        listOf(
            ClientField.GENDER.apiKey,
            ClientField.BIRTHDAY.apiKey,
            ClientField.BALANCE.apiKey,
        ),
)
