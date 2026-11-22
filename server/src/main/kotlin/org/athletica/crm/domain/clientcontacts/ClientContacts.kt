package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.storage.Transaction

/**
 * Контакт клиента: значение [value] определённого типа [type] (телефон, email, Telegram и т.п.).
 * Какие каналы умеют адресовать контакт — свойство вида канала
 * ([org.athletica.crm.core.messaging.ChannelConfig.addressing]), а не контакта.
 */
data class ClientContact(
    /** Идентификатор контакта. */
    val id: ClientContactId,
    /** Клиент, которому принадлежит контакт. */
    val clientId: ClientId,
    /** Тип контакта. */
    val type: ContactType,
    /** Значение контакта: номер, адрес, имя пользователя. */
    val value: String,
    /** Свободный комментарий, например «мама»; `null` — без комментария. */
    val label: String? = null,
    /** Основной контакт своего типа: на него звонят и пишут по умолчанию. */
    val isPrimary: Boolean = false,
)

/** Максимальная длина комментария к контакту после обрезки пробелов. */
const val MAX_CONTACT_LABEL_LENGTH = 100

/**
 * Набор контактов [this] в том виде, в котором он хранится: комментарии без пробелов по краям
 * (пустой — `null`), ровно один основной в каждом типе (если не отмечен — первый контакт типа),
 * контакты одного типа подряд в порядке появления типа, основной — первым в типе.
 * Два основных в одном типе — ошибка `CONTACT_PRIMARY_DUPLICATE`, комментарий длиннее
 * [MAX_CONTACT_LABEL_LENGTH] — `CONTACT_LABEL_TOO_LONG`.
 */
context(raise: Raise<DomainError>)
fun List<ClientContact>.normalized(): List<ClientContact> =
    groupBy { it.type }.values.flatMap { sameType ->
        if (sameType.count { it.isPrimary } > 1) {
            raise(CommonDomainError("CONTACT_PRIMARY_DUPLICATE", "У клиента может быть только один основной контакт каждого типа"))
        }
        val primary = sameType.firstOrNull { it.isPrimary } ?: sameType.first()
        (listOf(primary) + sameType.filter { it !== primary }).map {
            val label = it.label?.trim()?.ifEmpty { null }
            if (label != null && label.length > MAX_CONTACT_LABEL_LENGTH) {
                raise(CommonDomainError("CONTACT_LABEL_TOO_LONG", "Комментарий к контакту длиннее $MAX_CONTACT_LABEL_LENGTH символов"))
            }
            it.copy(label = label, isPrimary = it === primary)
        }
    }

/** Репозиторий контактов клиентов. Все операции изолированы по организации. */
interface ClientContacts {
    /** Возвращает контакты клиента [clientId] в порядке хранения (см. [normalized]). */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): List<ClientContact>

    /** Возвращает контакты для набора клиентов [clientIds], сгруппированные по идентификатору клиента. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClients(clientIds: List<ClientId>): Map<ClientId, List<ClientContact>>

    /**
     * Клиенты организации, у которых есть телефон, совпадающий с [phone] без учёта форматирования
     * (нормализация как у [PhoneDigits]). Каждый клиент встречается один раз.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun clientsByPhone(phone: PhoneDigits): List<ClientId>

    /** Полностью заменяет набор контактов клиента [client] на [contacts], приведённый к [normalized]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun replace(client: ActiveClient, contacts: List<ClientContact>)
}
