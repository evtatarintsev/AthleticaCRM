package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Репозиторий доступов к кабинету клиента. */
interface ClientAppAccesses {
    /**
     * Доступы по каждому телефону клиента [clientId] организации запроса в порядке контактов,
     * по одному на номер. Номер без приглашения — [NotInvitedClientAppAccess].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): List<ClientAppAccess>

    /**
     * Неприглашённые номера активных клиентов организации запроса — для массового приглашения.
     * [groupIds] `null` — все клиенты, иначе только участники этих групп.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun notInvited(groupIds: List<GroupId>?): List<NotInvitedClientAppAccess>

    /**
     * Доступы номера [phone] во всех организациях, которые сейчас могут давать данные:
     * кабинет организации включён и клиент не в архиве. Остальные доступы сохраняют статус,
     * но не возвращаются — восстановление клиента или включение кабинета возвращает их как были.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun byPhone(phone: PhoneDigits): List<StoredClientAppAccess>

    /**
     * Все хранимые доступы номера [phone] во всех организациях, в том числе с выключенным кабинетом,
     * у клиентов в архиве и у номеров, которых уже нет в контактах, — для удаления аккаунта.
     * Строки блокируются до конца транзакции: параллельная смена статуса ждёт, а не ломает удаление.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun allByPhone(phone: PhoneDigits): List<StoredClientAppAccess>

    /**
     * Отмечает активность профиля [ctx] в момент [now] — время последней активности доступа, которое
     * видит организация. Пишет, только если доступ подключён и с прошлой отметки прошло больше 5 минут;
     * доступы других организаций не трогает.
     */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun touch(now: Instant)

    /**
     * Закрепляет задачу [taskId] «Проверьте номер телефона» за доступом [access] после «Это не я».
     * `false` — у доступа уже есть открытая такая задача, новую создавать не нужно.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun reserveNotMeTask(access: NotMeClientAppAccess, taskId: TaskId): Boolean
}
