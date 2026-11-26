package org.athletica.crm.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.domain.clientapp.auth.IssuedClientAppSession
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Токены кабинета клиента. Аудитория [AUDIENCE] отличается от токенов сотрудников, поэтому
 * верификатор CRM не принимает клиентский токен, а [verifier] — токен сотрудника.
 * [secret] — ключ HMAC256, [accessTokenTtlMinutes] и [refreshTokenTtlDays] — сроки жизни токенов.
 */
class ClientAppJwtConfig(secret: String, accessTokenTtlMinutes: Long, refreshTokenTtlDays: Long) {
    private val algorithm = Algorithm.HMAC256(secret)

    /** Время жизни access токена; столько же живёт cookie с ним. */
    val accessTokenTtl: Duration = accessTokenTtlMinutes.minutes

    /** Время жизни refresh токена; столько же живёт cookie с ним и неактивный сеанс. */
    val refreshTokenTtl: Duration = refreshTokenTtlDays.days

    /** Верификатор подписи, издателя и аудитории клиентских токенов. */
    val verifier: JWTVerifier =
        JWT
            .require(algorithm)
            .withIssuer(JwtConfig.ISSUER)
            .withAudience(AUDIENCE)
            .build()

    /** Подписанный access токен аккаунта [accountId] в сеансе [session]. */
    fun accessToken(accountId: ClientAppAccountId, session: IssuedClientAppSession): String =
        JWT
            .create()
            .withIssuer(JwtConfig.ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_ACCOUNT_ID, accountId.toString())
            .withClaim(JwtConfig.CLAIM_SESSION_ID, session.id.toString())
            .withClaim(JwtConfig.CLAIM_TYPE, JwtConfig.TYPE_ACCESS)
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenTtl.inWholeMilliseconds))
            .sign(algorithm)

    /** Подписанный refresh токен аккаунта [accountId]; несёт сеанс [session] и его действующий `jti`. */
    fun refreshToken(accountId: ClientAppAccountId, session: IssuedClientAppSession): String =
        JWT
            .create()
            .withIssuer(JwtConfig.ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_ACCOUNT_ID, accountId.toString())
            .withClaim(JwtConfig.CLAIM_SESSION_ID, session.id.toString())
            .withJWTId(session.refreshJti.toString())
            .withClaim(JwtConfig.CLAIM_TYPE, JwtConfig.TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenTtl.inWholeMilliseconds))
            .sign(algorithm)

    companion object {
        /** Аудитория клиентских токенов. */
        const val AUDIENCE = "athletica-client-app"

        /** Claim с идентификатором аккаунта кабинета. */
        const val CLAIM_ACCOUNT_ID = "accountId"

        /** Имя cookie с access токеном кабинета; отличается от cookie CRM. */
        const val COOKIE_ACCESS_TOKEN = "client_app_access_token"

        /** Имя cookie с refresh токеном кабинета; отличается от cookie CRM. */
        const val COOKIE_REFRESH_TOKEN = "client_app_refresh_token"
    }
}
