package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.LeadSourceId

/**
 * Решение пользователя для конкретного уникального значения из CSV-колонки источника:
 * связать с существующим источником, создать новый или пропустить (источник не будет проставлен).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class LeadSourceAction {
    /** Использовать существующий источник [id] из справочника организации. */
    @Serializable
    @SerialName("use_existing")
    data class UseExisting(val id: LeadSourceId) : LeadSourceAction()

    /** Создать новый источник с именем [name] и привязать клиентов с этим значением к нему. */
    @Serializable
    @SerialName("create_new")
    data class CreateNew(val name: String) : LeadSourceAction()

    /** Не проставлять источник клиентам с этим значением. */
    @Serializable
    @SerialName("skip")
    data object Skip : LeadSourceAction()
}
