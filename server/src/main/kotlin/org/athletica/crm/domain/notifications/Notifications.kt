package org.athletica.crm.domain.notifications

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.NotificationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Коллекция уведомлений организации: фабрика новых уведомлений, чтение уведомлений
 * текущего сотрудника и массовые операции над ними.
 */
interface Notifications {
    /**
     * Создаёт новое (ещё не сохранённое) уведомление [title]/[body] для [recipients].
     * Для сохранения вызвать [Notification.save].
     */
    fun new(title: String, body: String, recipients: List<EmployeeId>): Notification

    /**
     * Возвращает уведомления текущего сотрудника ([ctx]) в рамках его организации.
     *
     * [isRead] — фильтр: `true` — только прочитанные, `false` — только непрочитанные, `null` — все.
     * Возвращает не более 50 последних, отсортированных по убыванию [Notification.createdAt].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun of(isRead: Boolean? = null): List<Notification>

    /** Возвращает число непрочитанных уведомлений текущего сотрудника ([ctx]). */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unreadCount(): Int

    /**
     * Отмечает уведомления [ids] прочитанными для текущего сотрудника ([ctx]).
     * Чужие и несуществующие id молча игнорируются; пустой список — ничего не делает.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markAsRead(ids: List<NotificationId>)

    /** Отмечает все уведомления текущего сотрудника ([ctx]) прочитанными. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markAllAsRead()
}
