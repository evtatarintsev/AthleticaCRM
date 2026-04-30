package org.athletica.crm.components.clients

import kotlinx.datetime.LocalDate
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId

/**
 * Поля формы создания / редактирования клиента.
 * [isValid] — форма готова к отправке (имя не пустое).
 */
data class ClientForm(
    val name: String = "",
    val gender: Gender = Gender.MALE,
    val birthday: LocalDate? = null,
    val avatarId: UploadId? = null,
    val leadSourceId: LeadSourceId? = null,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

/** Состояние операции сохранения клиента (создание или редактирование). */
sealed class ClientSaveState {
    /** Форма ожидает отправки. */
    data object Idle : ClientSaveState()

    /** Запрос выполняется. */
    data object Saving : ClientSaveState()

    /** Сервер вернул ошибку [error]. */
    data class Error(val error: ClientsApiError) : ClientSaveState()
}
