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
 * Принимает [db] — обёртку над пулом R2DBC соединений и [passwordHasher] — сервис хеширования паролей.
 */
class SignUp(private val db: Database, private val passwordHasher: PasswordHasher) {
    /**
     * Регистрирует новую организацию и её владельца по данным [request].
     * Возвращает созданного пользователя.
     */
    suspend fun signUp(request: SignUpRequest): User {
        val orgId = Uuid.generateV7()
        val userId = Uuid.generateV7()

        db.transaction {
            sql("INSERT INTO organizations (id, name) VALUES (:orgId, :orgName)")
                .bind("orgId", orgId)
                .bind("orgName", request.companyName)
                .execute()

            sql("INSERT INTO users (id, login, name, password_hash) VALUES (:userId, :login, :userName, :hash)")
                .bind("userId", userId)
                .bind("login", request.login)
                .bind("userName", request.userName)
                .bind("hash", passwordHasher.hash(request.password).value)
                .execute()

            sql("INSERT INTO employees (user_id, org_id, is_owner) VALUES (:userId, :orgId, true)")
                .bind("orgId", orgId)
                .bind("userId", userId)
                .execute()
        }

        return User(id = userId, username = request.login)
    }
}
