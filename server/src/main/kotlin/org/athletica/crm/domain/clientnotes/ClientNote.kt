package org.athletica.crm.domain.clientnotes

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ClientNoteId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Неизменяемый агрегат заметки о клиенте.
 * Все трансформации возвращают новый экземпляр; единственная мутация в БД — через [save] и [delete].
 * Редактировать и удалять заметку может только её автор; проверка выполняется в домене.
 */
interface ClientNote {
    val id: ClientNoteId

    /** Идентификатор организации-владельца заметки. */
    val orgId: OrgId

    /** Идентификатор клиента, к которому относится заметка. */
    val clientId: ClientId

    /** Сотрудник, создавший заметку. */
    val authorId: EmployeeId

    val text: ClientNoteText

    val createdAt: Instant

    /** Время последнего редактирования; null если заметку не редактировали. */
    val updatedAt: Instant?

    /**
     * Сохраняет актуальное состояние заметки в БД, обновляя текст и [updatedAt].
     * Используется после [withText].
     */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /**
     * Помечает заметку как удалённую (soft-delete).
     * Требует, чтобы текущий сотрудник был автором заметки, иначе возвращает `PERMISSION_DENIED`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /**
     * Возвращает копию заметки с обновлённым [newText] и заполненным `updatedAt`.
     * Требует, чтобы текущий сотрудник был автором заметки, иначе возвращает `PERMISSION_DENIED`.
     * Изменения применяются к БД через [save].
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun withText(newText: ClientNoteText): ClientNote
}
