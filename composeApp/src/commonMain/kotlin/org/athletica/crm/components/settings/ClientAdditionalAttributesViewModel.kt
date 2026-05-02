package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.SaveCustomFieldsRequest

/** Состояние загрузки дополнительных атрибутов клиента. */
sealed class ClientAdditionalAttributesLoadState {
    /** Первичная загрузка списка. */
    data object Loading : ClientAdditionalAttributesLoadState()

    /** Список успешно загружен. */
    data class Loaded(val attributes: List<CustomFieldDefinitionSchema>) : ClientAdditionalAttributesLoadState()

    /** Ошибка загрузки списка. */
    data class Error(val error: SettingsApiError) : ClientAdditionalAttributesLoadState()
}

/** Состояние операции сохранения дополнительных атрибутов клиента. */
sealed class ClientAdditionalAttributesSaveState {
    /** Ожидание действий пользователя. */
    data object Idle : ClientAdditionalAttributesSaveState()

    /** Выполняется сохранение. */
    data object Saving : ClientAdditionalAttributesSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : ClientAdditionalAttributesSaveState()
}

/**
 * ViewModel экрана «Дополнительные атрибуты клиентов».
 * Загружает список полей и сохраняет обновлённый полный набор в API.
 */
class ClientAdditionalAttributesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<ClientAdditionalAttributesLoadState>(ClientAdditionalAttributesLoadState.Loading)
        private set

    var saveState by mutableStateOf<ClientAdditionalAttributesSaveState>(ClientAdditionalAttributesSaveState.Idle)
        private set

    init {
        getAttributes()
    }

    /** Загружает список кастомных полей для сущности клиента. */
    fun getAttributes() {
        scope.launch {
            loadState = ClientAdditionalAttributesLoadState.Loading
            api.customFields.list(entityType = CLIENT_ENTITY_TYPE).fold(
                ifLeft = { loadState = ClientAdditionalAttributesLoadState.Error(it.toSettingsApiError()) },
                ifRight = { fields -> loadState = ClientAdditionalAttributesLoadState.Loaded(fields) },
            )
        }
    }

    /**
     * Сохраняет созданный или изменённый атрибут.
     *
     * В API отправляется полный актуальный список полей.
     */
    fun saveAttribute(
        updated: CustomFieldDefinitionSchema,
        isNew: Boolean,
        onSuccess: () -> Unit = {},
    ) {
        scope.launch {
            val currentAttributes = (loadState as? ClientAdditionalAttributesLoadState.Loaded)?.attributes ?: emptyList()
            val updatedAttributes =
                if (isNew) {
                    currentAttributes + updated
                } else {
                    currentAttributes.map {
                        if (it.fieldKey == updated.fieldKey) {
                            updated
                        } else {
                            it
                        }
                    }
                }

            saveAllAttributes(updatedAttributes, onSuccess)
        }
    }

    /** Удаляет один атрибут по [fieldKey], после чего сохраняет полный список. */
    fun deleteAttribute(
        fieldKey: String,
        onSuccess: () -> Unit = {},
    ) {
        scope.launch {
            val currentAttributes = (loadState as? ClientAdditionalAttributesLoadState.Loaded)?.attributes ?: emptyList()
            val updatedAttributes = currentAttributes.filter { it.fieldKey != fieldKey }
            saveAllAttributes(updatedAttributes, onSuccess)
        }
    }

    /** Удаляет набор атрибутов по [fieldKeys], после чего сохраняет полный список. */
    fun deleteAttributes(
        fieldKeys: Set<String>,
        onSuccess: () -> Unit = {},
    ) {
        scope.launch {
            val currentAttributes = (loadState as? ClientAdditionalAttributesLoadState.Loaded)?.attributes ?: emptyList()
            val updatedAttributes = currentAttributes.filterNot { it.fieldKey in fieldKeys }
            saveAllAttributes(updatedAttributes, onSuccess)
        }
    }

    /** Сбрасывает ошибку сохранения после её отображения пользователю. */
    fun onSaveErrorDismissed() {
        if (saveState is ClientAdditionalAttributesSaveState.Error) {
            saveState = ClientAdditionalAttributesSaveState.Idle
        }
    }

    /** Сохраняет полный список полей в API и обновляет локальное состояние. */
    private suspend fun saveAllAttributes(
        attributes: List<CustomFieldDefinitionSchema>,
        onSuccess: () -> Unit,
    ) {
        saveState = ClientAdditionalAttributesSaveState.Saving
        api.customFields.save(
            entityType = CLIENT_ENTITY_TYPE,
            request = SaveCustomFieldsRequest(fields = attributes),
        ).fold(
            ifLeft = { saveState = ClientAdditionalAttributesSaveState.Error(it.toSettingsApiError()) },
            ifRight = { savedFields ->
                loadState = ClientAdditionalAttributesLoadState.Loaded(savedFields)
                saveState = ClientAdditionalAttributesSaveState.Idle
                onSuccess()
            },
        )
    }

    private companion object {
        const val CLIENT_ENTITY_TYPE = "CLIENT"
    }
}
