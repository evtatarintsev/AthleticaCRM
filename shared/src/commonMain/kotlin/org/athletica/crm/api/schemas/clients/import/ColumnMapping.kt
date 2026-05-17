package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.Serializable

/**
 * Соответствие между колонкой CSV и атрибутом клиента.
 * [sourceColumn] — заголовок колонки в загруженном файле; [target] — атрибут клиента или Skip.
 */
@Serializable
data class ColumnMapping(
    val sourceColumn: String,
    val target: ImportTarget,
)
