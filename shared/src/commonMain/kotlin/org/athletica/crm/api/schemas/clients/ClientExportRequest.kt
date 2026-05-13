package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/**
 * Запрос экспорта клиентов.
 * [fields] — упорядоченный список ключей полей, которые попадут в файл. Ключи могут быть
 * из [ClientField] (стандартные) либо ключами кастомных полей клиента. Колонка «Имя»
 * всегда первая и не указывается отдельно.
 */
@Serializable
data class ClientExportRequest(
    val fields: List<String>,
)
