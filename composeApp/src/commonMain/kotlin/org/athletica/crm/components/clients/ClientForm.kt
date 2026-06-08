package org.athletica.crm.components.clients

import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.core.Gender
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldValues
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
    val customFields: CustomFieldValues = CustomFieldValues(emptyList()),
    val contacts: List<ContactFormEntry> = listOf(ContactFormEntry()),
) {
    val isValid: Boolean get() = name.isNotBlank()

    /** Контакты для отправки на сервер: непустые значения с обрезанными пробелами. */
    fun contactInputs(): List<ClientContactInput> =
        contacts
            .filter { it.value.isNotBlank() }
            .map { ClientContactInput(it.type, it.value.trim()) }
}

/**
 * Строка формы контактов: тип [type] и значение [value].
 * По умолчанию — пустой телефон (главный кейс быстрого ввода).
 */
data class ContactFormEntry(
    val type: ContactType = ContactType.PHONE,
    val value: String = "",
)

/** Состояние операции сохранения клиента (создание или редактирование). */
sealed class ClientSaveState {
    /** Форма ожидает отправки. */
    data object Idle : ClientSaveState()

    /** Запрос выполняется. */
    data object Saving : ClientSaveState()

    /** Сервер вернул ошибку [error]. */
    data class Error(val error: ClientsApiError) : ClientSaveState()
}
