package org.athletica.crm.usecases

import org.athletica.crm.api.schemas.SignUpRequest
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.db.Database
import org.athletica.crm.security.PasswordHasher
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** Зарегистрированный пользователь без данных о пароле. */
data class User(override val id: Uuid, override val username: String) : AuthenticatedUser

/**
 * Use case регистрации новой организации и её владельца.
 *
 * Создаёт в одной транзакции организацию, пользователя и запись сотрудника
 * с флагом [is_owner = true][org.athletica.crm.db.Database.transaction].
 * UUID генерируются на клиенте; все три INSERT выполняются одним запросом
 * через data-modifying CTE.
 *
 * @param db обёртка над пулом R2DBC соединений
 * @param passwordHasher сервис хеширования паролей
 */
class SignUp(private val db: Database, private val passwordHasher: PasswordHasher) {
    /**
     * Регистрирует новую организацию и её владельца.
     *
     * @param request данные для регистрации
     * @return созданный пользователь
     */
    suspend fun signUp(request: SignUpRequest): User {
        val orgId = Uuid.generateV7()
        val userId = Uuid.generateV7()

        db.transaction {
            sql(
                """
                WITH
                  new_org AS (
                    INSERT INTO organizations (id, name)
                    VALUES (:orgId, :orgName)
                  ),
                  new_user AS (
                    INSERT INTO users (id, login, name, password_hash)
                    VALUES (:userId, :login, :userName, :hash)
                  )
                INSERT INTO employees (user_id, org_id, is_owner)
                VALUES (:userId, :orgId, true)
                """.trimIndent()
            )
                .bind("orgId", orgId.toJavaUuid())
                .bind("orgName", request.companyName)
                .bind("userId", userId.toJavaUuid())
                .bind("login", request.login)
                .bind("userName", request.userName)
                .bind("hash", passwordHasher.hash(request.password).value)
                .firstOrNull { _, _ -> Unit }
        }

        return User(id = userId, username = request.login)
    }
}
