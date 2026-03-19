package org.athletica.crm.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"

    const val ISSUER = "athletica-crm"
    const val AUDIENCE = "athletica-crm-users"
    const val CLAIM_USER_ID = "userId"
    const val CLAIM_USERNAME = "username"
    const val CLAIM_TYPE = "type"
    const val TYPE_ACCESS = "access"
    const val TYPE_REFRESH = "refresh"

    const val COOKIE_ACCESS_TOKEN = "access_token"
    const val COOKIE_REFRESH_TOKEN = "refresh_token"

    private val ACCESS_TOKEN_TTL = 15 * 60 * 1000L // 15 минут
    private val REFRESH_TOKEN_TTL = 30L * 24 * 60 * 60 * 1000 // 30 дней

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

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
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL))
            .sign(algorithm)

    fun makeRefreshToken(userId: Int): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId)
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_TTL))
            .sign(algorithm)
}
