package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/**
 * Каталог стандартных колонок таблицы списка клиентов. Отделён от каталога полей
 * экспорта [ClientField]: пол и дата рождения показываются под именем, а не колонками.
 * Имя клиента в каталоге не присутствует — оно отображается всегда. Контакты и
 * кастомные поля каталогом не покрываются.
 */
@Serializable
enum class ClientListColumn(
    /** Стабильный ключ для сохранения в настройках отображения. */
    val apiKey: String,
) {
    GROUPS("groups"),
    MEMBERSHIPS("memberships"),
    BALANCE("balance"),
}
