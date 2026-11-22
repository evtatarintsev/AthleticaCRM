package org.athletica.crm.domain.clientcontacts

import arrow.core.Either
import arrow.core.raise.context.either
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Тесты приведения набора контактов к хранимому виду ([normalized]). */
class ClientContactsNormalizedTest {
    private val clientId = ClientId.new()

    private fun contact(
        type: ContactType,
        value: String,
        label: String? = null,
        isPrimary: Boolean = false,
    ) = ClientContact(ClientContactId.new(), clientId, type, value, label, isPrimary)

    private fun List<ClientContact>.normalizedOrError(): Either<DomainError, List<ClientContact>> = either { normalized() }

    private fun List<ClientContact>.stored(): List<ClientContact> = assertIs<Either.Right<List<ClientContact>>>(normalizedOrError()).value

    @Test
    fun `единственный телефон становится основным`() {
        val stored = listOf(contact(ContactType.PHONE, "+79990001122")).stored()

        assertEquals(listOf(true), stored.map { it.isPrimary })
    }

    @Test
    fun `без отметки основным становится первый контакт типа`() {
        val stored =
            listOf(
                contact(ContactType.PHONE, "1", "мама"),
                contact(ContactType.PHONE, "2", "папа"),
                contact(ContactType.PHONE, "3", "бабушка"),
            ).stored()

        assertEquals(listOf("мама" to true, "папа" to false, "бабушка" to false), stored.map { it.label to it.isPrimary })
    }

    @Test
    fun `явно отмеченный основной идёт первым в своём типе`() {
        val stored =
            listOf(
                contact(ContactType.PHONE, "1", "мама"),
                contact(ContactType.PHONE, "2", "папа", isPrimary = true),
            ).stored()

        assertEquals(listOf("папа" to true, "мама" to false), stored.map { it.label to it.isPrimary })
    }

    @Test
    fun `типы независимы и не перемешиваются`() {
        val stored =
            listOf(
                contact(ContactType.PHONE, "1", "мама"),
                contact(ContactType.EMAIL, "a@b.c"),
                contact(ContactType.PHONE, "2", "бабушка", isPrimary = true),
            ).stored()

        assertEquals(
            listOf(
                Triple(ContactType.PHONE, "2", true),
                Triple(ContactType.PHONE, "1", false),
                Triple(ContactType.EMAIL, "a@b.c", true),
            ),
            stored.map { Triple(it.type, it.value, it.isPrimary) },
        )
    }

    @Test
    fun `комментарий обрезается, пустой становится null`() {
        val stored =
            listOf(
                contact(ContactType.PHONE, "1", " мама "),
                contact(ContactType.PHONE, "2", "   "),
            ).stored()

        assertEquals("мама", stored[0].label)
        assertNull(stored[1].label)
    }

    @Test
    fun `два основных одного типа отклоняются`() {
        val result =
            listOf(
                contact(ContactType.PHONE, "1", isPrimary = true),
                contact(ContactType.PHONE, "2", isPrimary = true),
            ).normalizedOrError()

        val error = assertIs<Either.Left<DomainError>>(result).value
        assertEquals("CONTACT_PRIMARY_DUPLICATE", assertIs<CommonDomainError>(error).code)
    }

    @Test
    fun `комментарий длиннее предела отклоняется, ровно на пределе — принимается`() {
        val atLimit = listOf(contact(ContactType.PHONE, "1", " " + "м".repeat(MAX_CONTACT_LABEL_LENGTH) + " ")).stored()
        val result = listOf(contact(ContactType.PHONE, "1", "м".repeat(MAX_CONTACT_LABEL_LENGTH + 1))).normalizedOrError()

        assertEquals(MAX_CONTACT_LABEL_LENGTH, atLimit[0].label?.length)
        val error = assertIs<Either.Left<DomainError>>(result).value
        assertEquals("CONTACT_LABEL_TOO_LONG", assertIs<CommonDomainError>(error).code)
    }
}
