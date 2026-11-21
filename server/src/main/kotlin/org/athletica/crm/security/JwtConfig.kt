package org.athletica.crm.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.domain.auth.IssuedSession
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Конфигурация и фабрика JWT токенов.
 * [secret] — секретный ключ HMAC256 для подписи,
 * [accessTokenTtlMinutes] — время жизни access токена в минутах,
 * [refreshTokenTtlDays] — время жизни refresh токена в днях.
 */
class JwtConfig(secret: String, accessTokenTtlMinutes: Long, refreshTokenTtlDays: Long) {
    private val algorithm = Algorithm.HMAC256(secret)

    /** Время жизни access токена; столько же живёт cookie с ним. */
    val accessTokenTtl: Duration = accessTokenTtlMinutes.minutes

    /** Время жизни refresh токена; столько же живёт cookie с ним. */
    val refreshTokenTtl: Duration = refreshTokenTtlDays.days

    /** Верификатор для проверки подписи и claims входящих токенов. */
    val verifier: JWTVerifier =
        JWT
            .require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

    /** Подписанный JWT access токен пользователя [user] в сессии [sessionId]. */
    fun makeAccessToken(user: AuthenticatedUser, sessionId: AuthSessionId): String =
        JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, user.id.toString())
            .withClaim(CLAIM_ORG_ID, user.orgId.toString())
            .withClaim(CLAIM_BRANCH_ID, user.branchId.toString())
            .withClaim(CLAIM_EMPLOYEE_ID, user.employeeId.toString())
            .withClaim(CLAIM_USERNAME, user.username)
            .withClaim(CLAIM_SESSION_ID, sessionId.toString())
            .withClaim(CLAIM_TYPE, TYPE_ACCESS)
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenTtl.inWholeMilliseconds))
            .sign(algorithm)

    /**
     * Подписанный JWT refresh токен пользователя [userId] в филиале [branchId];
     * принадлежит сессии [session] и несёт её действующий `jti`.
     */
    fun makeRefreshToken(userId: UserId, branchId: BranchId, session: IssuedSession): String =
        JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId.toString())
            .withClaim(CLAIM_BRANCH_ID, branchId.toString())
            .withClaim(CLAIM_SESSION_ID, session.id.toString())
            .withJWTId(session.refreshJti.toString())
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenTtl.inWholeMilliseconds))
            .sign(algorithm)

    companion object {
        /** Издатель токенов. */
        const val ISSUER = "athletica-crm"

        /** Аудитория токенов. */
        const val AUDIENCE = "athletica-crm-users"

        /** Claim с идентификатором пользователя. */
        const val CLAIM_USER_ID = "userId"

        /** Claim с идентификатором организации пользователя. */
        const val CLAIM_ORG_ID = "orgId"

        /** Claim с идентификатором филиала, в котором авторизован пользователь. */
        const val CLAIM_BRANCH_ID = "branchId"

        /** Claim с идентификатором сотрудника пользователя. */
        const val CLAIM_EMPLOYEE_ID = "employeeId"

        /** Claim с именем пользователя. */
        const val CLAIM_USERNAME = "username"

        /** Claim с идентификатором сессии входа. */
        const val CLAIM_SESSION_ID = "sid"

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
