package org.athletica.crm.domain.enrollments

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Репозиторий записей клиентов в группы.
 *
 * Каждый период участия хранится отдельной строкой: повторная запись клиента
 * после выхода создаёт новую [Enrollment], история предыдущих периодов сохраняется.
 */
interface Enrollments {
    /**
     * Добавляет [clientIds] в группу [groupId].
     * Проверяет принадлежность группы организации из контекста.
     * Идемпотентен: если клиент уже активен в группе — повторная запись игнорируется.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun add(groupId: GroupId, clientIds: List<ClientId>)

    /**
     * Деактивирует участие [clientIds] в группе [groupId] (soft delete).
     * Проверяет принадлежность группы организации из контекста.
     * Если клиент не был активен — операция игнорируется.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun remove(groupId: GroupId, clientIds: List<ClientId>)

    /**
     * Возвращает записи клиентов, чьё участие пересекается с периодом [[from], [to]].
     * Пересечение: клиент записался до конца периода и не вышел до его начала.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun activeIn(groupId: GroupId, from: LocalDate, to: LocalDate): List<Enrollment>

    /** Возвращает записи клиентов, активных в конкретный [date]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun activeOn(groupId: GroupId, date: LocalDate): List<Enrollment> = activeIn(groupId, date, date)
}
