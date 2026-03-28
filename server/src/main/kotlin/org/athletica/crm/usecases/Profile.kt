package org.athletica.crm.usecases

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Database
import org.athletica.crm.security.User
import org.athletica.crm.security.userById

/**
 * Возвращает профиль текущего авторизованного пользователя.
 * Использует [ctx] для получения идентификатора пользователя из JWT.
 * Возвращает [User] или [CommonDomainError], если пользователь не найден.
 */
context(db: Database, ctx: RequestContext)
suspend fun profile(): Either<DomainError, User> =
    either {
        userById(ctx.userId.value).bind()
    }
