package org.athletica.crm.usecases.notifications

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.storage.Database

/**
 * Отмечает уведомления из [request] прочитанными для текущего пользователя ([ctx]).
 * Фильтрует по [RequestContext.userId] и [RequestContext.orgId] — чужие id молча игнорируются.
 * Если список пуст — ничего не делает.
 */
context(db: Database, ctx: RequestContext)
suspend fun markNotificationsRead(request: MarkNotificationsReadRequest): Either<CommonDomainError, Unit> {
    if (request.ids.isEmpty()) return Unit.right()

    db.sql(
        """
        UPDATE notification_recipients nr
        SET is_read = true, read_at = now()
        WHERE nr.notification_id = ANY(:ids)
          AND nr.user_id = :userId
          AND EXISTS (
              SELECT 1 FROM notifications n
              WHERE n.id = nr.notification_id AND n.org_id = :orgId
          )
        """.trimIndent(),
    )
        .bind("ids", request.ids)
        .bind("userId", ctx.userId)
        .bind("orgId", ctx.orgId)
        .execute()

    return Unit.right()
}

/**
 * Отмечает все уведомления текущего пользователя ([ctx]) прочитанными в рамках его организации.
 */
context(db: Database, ctx: RequestContext)
suspend fun markAllNotificationsRead(): Either<CommonDomainError, Unit> {
    db.sql(
        """
        UPDATE notification_recipients nr
        SET is_read = true, read_at = now()
        WHERE nr.user_id = :userId
          AND nr.is_read = false
          AND EXISTS (
              SELECT 1 FROM notifications n
              WHERE n.id = nr.notification_id AND n.org_id = :orgId
          )
        """.trimIndent(),
    )
        .bind("userId", ctx.userId)
        .bind("orgId", ctx.orgId)
        .execute()

    return Unit.right()
}
