package org.athletica.crm.security

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.athletica.crm.core.errors.CommonDomainError
import java.util.Date
import kotlin.uuid.Uuid

/**
 * Конфигурация и фабрика JWT токенов.
 * [secret] — секретный ключ HMAC256 для подписи,
 * [accessTokenTtlMinutes] — время жизни access токена в минутах,
 * [refreshTokenTtlDays] — время жизни refresh токена в днях.
 */
class JwtConfig(
    secret: String,
    accessTokenTtlMinutes: Long,
    refreshTokenTtlDays: Long,
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val accessTokenTtlMs = accessTokenTtlMinutes * 60 * 1000
    private val refreshTokenTtlMs = refreshTokenTtlDays * 24 * 60 * 60 * 1000

    /** Верификатор для проверки подписи и claims входящих токенов. */
    val verifier: JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

    /**
     * Создаёт подписанный JWT access токен для пользователя с идентификатором [userId] и именем [username].
     */
    fun makeAccessToken(
        userId: Uuid,
        username: String,
    ): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId.toString())
            .withClaim(CLAIM_USERNAME, username)
            .withClaim(CLAIM_TYPE, TYPE_ACCESS)
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenTtlMs))
            .sign(algorithm)

    /**
     * Создаёт подписанный JWT refresh токен для пользователя с идентификатором [userId].
     */
    fun makeRefreshToken(userId: Uuid): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId.toString())
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenTtlMs))
            .sign(algorithm)

    fun verifyRefreshToken(refreshToken: String): Either<CommonDomainError, DecodedJWT> = runCatching {
        verifier.verify(refreshToken)
    }.getOrNull()?.right() ?: CommonDomainError("", "").left()

    companion object {
        /** Издатель токенов. */
        const val ISSUER = "athletica-crm"

        /** Аудитория токенов. */
        const val AUDIENCE = "athletica-crm-users"

        /** Claim с идентификатором пользователя. */
        const val CLAIM_USER_ID = "userId"

        /** Claim с именем пользователя. */
        const val CLAIM_USERNAME = "username"

        /** Claim с типом токена. */
        const val CLAIM_TYPE = "type"

        /** Значение claim типа для access токена. */
        const val TYPE_ACCESS = "access"

        /** Значение claim типа для refresh токена. */
        const val TYPE_REFRESH = "refresh"

        /** Имя cookie для хранения access токена. */
        const val COOKIE_ACCESS_TOKEN = "access_token"

        /** Имя cookie для хранения refresh токена. */
        const val COOKIE_REFRESH_TOKEN = "refresh_token"
    }
}
