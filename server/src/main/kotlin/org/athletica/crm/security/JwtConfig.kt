package org.athletica.crm.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    const val ISSUER = "athletica-crm"
    const val AUDIENCE = "athletica-crm-users"
    const val CLAIM_USER_ID = "userId"
    const val CLAIM_USERNAME = "username"
    const val CLAIM_TYPE = "type"
    const val TYPE_ACCESS = "access"
    const val TYPE_REFRESH = "refresh"

    const val COOKIE_ACCESS_TOKEN = "access_token"
    const val COOKIE_REFRESH_TOKEN = "refresh_token"

    private lateinit var algorithm: Algorithm
    private var accessTokenTtlMs: Long = 0
    private var refreshTokenTtlMs: Long = 0

    lateinit var verifier: JWTVerifier
        private set

    fun configure(
        secret: String,
        accessTokenTtlMinutes: Long,
        refreshTokenTtlDays: Long,
    ) {
        algorithm = Algorithm.HMAC256(secret)
        accessTokenTtlMs = accessTokenTtlMinutes * 60 * 1000
        refreshTokenTtlMs = refreshTokenTtlDays * 24 * 60 * 60 * 1000
        verifier =
            JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .build()
    }

    fun makeAccessToken(
        userId: Int,
        username: String,
    ): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId)
            .withClaim(CLAIM_USERNAME, username)
            .withClaim(CLAIM_TYPE, TYPE_ACCESS)
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenTtlMs))
            .sign(algorithm)

    fun makeRefreshToken(userId: Int): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId)
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenTtlMs))
            .sign(algorithm)
}
