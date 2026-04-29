package org.athletica.crm.security

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.right
import io.r2dbc.spi.Row
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Данные пользователя без привязки к филиалу. Используется до выбора филиала.
 * [password] — хэш пароля для верификации.
 */
data class VerifiedUser(
    val id: UserId,
    val orgId: OrgId,
    val employeeId: EmployeeId,
    val username: String,
    val password: String,
) {
    /** Создаёт [User] с указанным [branchId]. */
    fun withBranch(branchId: BranchId) = User(id, orgId, branchId, employeeId, username, password)
}

/**
 * Данные пользователя системы с выбранным филиалом.
 * [id] — уникальный идентификатор, [orgId] — организация пользователя, [username] — имя для входа, [password] — хэш пароля.
 */
data class User(
    override val id: UserId,
    override val orgId: OrgId,
    override val branchId: BranchId,
    override val employeeId: EmployeeId,
    override val username: String,
    val password: String,
) : AuthenticatedUser

/** Ошибка поиска пользователя: пользователь не найден по заданным критериям. */
data class UserNotFound(override val message: String) : DomainError {
    override val code: String = "USER_NOT_FOUND"
}

/**
 * Ищет пользователя по идентификатору [id] и [branchId].
 * Возвращает найденного пользователя, либо [UserNotFound].
 */
context(tr: Transaction, raise: Raise<UserNotFound>)
suspend fun userById(id: UserId, branchId: BranchId): User =
    tr.sql(
        """
        SELECT u.*, e.id as employee_id, e.org_id
        FROM users u
        JOIN employees e ON e.user_id = u.id
        WHERE u.id = :id AND e.is_active = true
        """.trimIndent(),
    )
        .bind("id", id)
        .firstOrNull { it.toVerifiedUser().withBranch(branchId) }
        ?: raise(UserNotFound("User with id='$id' not found"))

/** Делегирует вызов [userById] для данного [UserId] с указанным [branchId]. */
context(tr: Transaction, raise: Raise<UserNotFound>)
suspend fun UserId.mapUserById(branchId: BranchId) = userById(this, branchId)

/**
 * Верифицирует учётные данные и возвращает пользователя без привязки к филиалу.
 * Используется когда [branchId] ещё неизвестен (например, для получения списка доступных филиалов).
 */
context(db: Database, passwordHasher: PasswordHasher)
suspend fun verifyCredentials(username: String, password: String): Either<UserNotFound, VerifiedUser> {
    val user =
        db
            .sql(
                """
                SELECT u.*, e.id as employee_id, e.org_id
                FROM users u
                JOIN employees e ON e.user_id = u.id
                WHERE u.login = :username AND e.is_active = true
                """.trimIndent(),
            )
            .bind("username", username)
            .firstOrNull { it.toVerifiedUser() }
    if (user == null) {
        return UserNotFound("User with given credentials not found").left()
    }
    val passwordIsValid = passwordHasher.verify(password, PasswordHash(user.password))
    return if (passwordIsValid) user.right() else UserNotFound("User with given credentials not found").left()
}

/**
 * Ищет пользователя по имени, паролю и [branchId].
 *
 * Сначала ищет пользователя по логину, затем проверяет пароль через [PasswordHasher.verify].
 * Сравнение в коде обязательно, так как Argon2id использует случайную соль —
 * два хеша одного пароля всегда разные и не могут сравниваться в SQL.
 *
 * Возвращает пользователя если [username] найден и [password] верен, либо [UserNotFound].
 */
context(db: Database, passwordHasher: PasswordHasher)
suspend fun findByCredentials(username: String, password: String, branchId: BranchId): Either<UserNotFound, User> = verifyCredentials(username, password).map { it.withBranch(branchId) }

private fun Row.toVerifiedUser() =
    VerifiedUser(
        id = get("id", UUID::class.java)!!.toKotlinUuid().toUserId(),
        orgId = get("org_id", UUID::class.java)!!.toKotlinUuid().toOrgId(),
        employeeId = get("employee_id", UUID::class.java)!!.toKotlinUuid().toEmployeeId(),
        username = get("login", String::class.java)!!,
        password = get("password_hash", String::class.java)!!,
    )
