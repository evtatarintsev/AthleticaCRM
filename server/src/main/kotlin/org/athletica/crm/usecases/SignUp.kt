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
 * Создаёт в одной транзакции: организацию, пользователя, роль владельца
 * и запись сотрудника с флагом [is_owner = true][org.athletica.crm.db.Database.transaction].
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
    suspend fun signUp(request: SignUpRequest): User =
        db.transaction {
            val orgId =
                sql("INSERT INTO organizations (name) VALUES (:name) RETURNING id")
                    .bind("name", request.companyName)
                    .firstOrNull { row, _ -> row.get("id", java.util.UUID::class.java)!! }!!
                    .toKotlinUuid()

            val userId =
                sql("INSERT INTO users (login, name, password_hash) VALUES (:login, :name, :hash) RETURNING id")
                    .bind("login", request.login)
                    .bind("name", request.userName)
                    .bind("hash", passwordHasher.hash(request.password).value)
                    .firstOrNull { row, _ -> row.get("id", java.util.UUID::class.java)!! }!!
                    .toKotlinUuid()

            val roleId =
                sql("INSERT INTO roles (org_id, name) VALUES (:orgId, :name) RETURNING id")
                    .bind("orgId", orgId.toJavaUuid())
                    .bind("name", "Владелец")
                    .firstOrNull { row, _ -> row.get("id", java.util.UUID::class.java)!! }!!
                    .toKotlinUuid()

            sql("INSERT INTO employees (user_id, org_id, role_id, is_owner) VALUES (:userId, :orgId, :roleId, true)")
                .bind("userId", userId.toJavaUuid())
                .bind("orgId", orgId.toJavaUuid())
                .bind("roleId", roleId.toJavaUuid())
                .firstOrNull { _, _ -> Unit }

            User(id = userId, username = request.login)
        }
}
