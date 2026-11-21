package org.athletica.crm

import kotlinx.coroutines.runBlocking
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.auth.DbAuthSessions
import org.athletica.crm.domain.auth.IssuedSession
import org.athletica.crm.security.JwtConfig
import kotlin.time.Clock
import kotlin.time.Instant

/** Открывает в тестовой БД сессию входа пользователя [user], как это делает вход по паролю. */
fun openTestSession(user: AuthenticatedUser): IssuedSession =
    runBlocking {
        orThrow {
            TestPostgres.db.transaction {
                val now = Clock.System.now()
                DbAuthSessions().new(user.id, user.orgId, "test", null, now, Instant.DISTANT_PAST)
            }
        }
    }

/** Access токен пользователя [user] в новой сессии из [openTestSession]. */
fun JwtConfig.sessionAccessToken(user: AuthenticatedUser): String = makeAccessToken(user, openTestSession(user).id)
