package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.customfields.CustomFieldKey

/**
 * Цель маппинга колонки CSV на атрибут клиента.
 * Sealed-иерархия сериализуется полиморфно с дискриминатором `type`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ImportTarget {
    /** Колонка не импортируется. */
    @Serializable
    @SerialName("skip")
    data object Skip : ImportTarget()

    /** ФИО клиента (обязательное поле). Должно быть ровно одно соответствие в маппинге. */
    @Serializable
    @SerialName("name")
    data object Name : ImportTarget()

    /** Дата рождения клиента. */
    @Serializable
    @SerialName("birthday")
    data object Birthday : ImportTarget()

    /** Пол клиента. Уникальные значения колонки маппятся отдельно в [ClientImportCommitRequest.genderMapping]. */
    @Serializable
    @SerialName("gender")
    data object Gender : ImportTarget()

    /** Источник клиента. Уникальные значения колонки маппятся отдельно в [ClientImportCommitRequest.leadSourceMapping]. */
    @Serializable
    @SerialName("lead_source")
    data object LeadSource : ImportTarget()

    /** Начальный баланс клиента — заносится в журнал admin_credit / admin_debit. */
    @Serializable
    @SerialName("balance")
    data object Balance : ImportTarget()

    /** Кастомное поле клиента с указанным ключом. */
    @Serializable
    @SerialName("custom_field")
    data class CustomField(val key: CustomFieldKey) : ImportTarget()
}
