package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.attendance.DefaultAttendanceLabels
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.leadSource.DefaultLeadSources
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.domain.settings.UserDisplaySettings
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.UserNotFound
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbAuth(
    private val passwordHasher: PasswordHasher,
    private val employees: Employees,
    private val organizations: Organizations,
    private val branches: Branches,
    private val userSettings: UserDisplaySettings,
) : Auth {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun login(
        username: String,
        password: String,
        branchId: BranchId,
        lang: Lang,
        clientIp: String?,
    ): AuthenticatedUser {
        val user = verifyCredentials(username, password, branchId)
        context(user.bootstrapContext(lang, clientIp)) {
            if (branches.list().none { it.id == branchId }) {
                raise(CommonDomainError("BRANCH_ACCESS_DENIED", "Access to branch denied"))
            }
        }
        return user
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun availableBranches(
        username: String,
        password: String,
        lang: Lang,
        clientIp: String?,
    ) = context(verifyCredentials(username, password, BranchId.notSelected()).bootstrapContext(lang, clientIp)) {
        branches.list()
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun userById(id: UserId, branchId: BranchId): AuthenticatedUser =
        tr.sql(
            """
            SELECT u.id, u.login, e.id AS employee_id, e.org_id
            FROM users u
            JOIN employees e ON e.user_id = u.id
            WHERE u.id = :id AND e.is_active = true
            """.trimIndent(),
        )
            .bind("id", id)
            .firstOrNull { it.toAuthUser(branchId) }
            ?: raise(UserNotFound("User with id='$id' not found"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun switchBranch(branchId: BranchId): AuthenticatedUser {
        if (!ctx.employee.availableBranches.contains(branchId)) {
            raise(CommonDomainError("BRANCH_ACCESS_DENIED", "Access to branch denied"))
        }
        return userById(ctx.userId, branchId)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun logout() {
        // JWT не хранится на сервере. Cookie очищается в HTTP-маршруте.
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun updateMe(request: UpdateMeRequest) {
        val updatedRows =
            tr.sql("UPDATE employees SET name = :name, avatar_id = :avatarId WHERE user_id = :userId AND org_id = :orgId")
                .bind("name", request.name)
                .bind("avatarId", request.avatarId)
                .bind("userId", ctx.userId)
                .bind("orgId", ctx.orgId)
                .execute()

        if (updatedRows == 0L) raise(UserNotFound("Employee not found for user='${ctx.userId}'"))
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun changePassword(request: ChangePasswordRequest) {
        // Повторная смена пароля должна проверить хэш, сохранённый предыдущей транзакцией.
        val passwordHash =
            tr.sql("SELECT password_hash FROM users WHERE id = :id FOR UPDATE")
                .bind("id", ctx.userId)
                .firstOrNull { it.asString("password_hash") }
                ?: raise(CommonDomainError("USER_NOT_FOUND", "User not found"))

        if (!passwordHasher.verify(request.oldPassword, PasswordHash(passwordHash))) {
            raise(CommonDomainError(code = "WRONG_PASSWORD", message = Messages.WrongPassword.localize()))
        }

        tr.sql("UPDATE users SET password_hash = :hash WHERE id = :id")
            .bind("hash", passwordHasher.hash(request.newPassword).value)
            .bind("id", ctx.userId)
            .execute()
    }

    /** Регистрирует организацию и владельца, создаёт локализованные настройки по умолчанию. */
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun signup(request: SignUpRequest, lang: Lang, clientIp: String?): AuthenticatedUser {
        val orgId = OrgId.new()
        val userId = UserId.new()
        val employeeId = EmployeeId.new()
        val branchId = BranchId.new()
        val hallId = HallId.new()
        tr.sql("INSERT INTO organizations (id, name, timezone, currency) VALUES (:orgId, :orgName, :timezone, :currency)")
            .bind("orgId", orgId)
            .bind("orgName", request.companyName)
            .bind("timezone", request.timezone)
            .bind("currency", request.currency.code)
            .execute()

        tr.sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
            .bind("userId", userId)
            .bind("login", request.login)
            .bind("hash", passwordHasher.hash(request.password).value)
            .onViolation("users_login_key") { SignUpError.UserAlreadyRegistered }
            .execute()

        tr.sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:employeeId, :userId, :orgId, :name, true)")
            .bind("employeeId", employeeId)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .bind("name", request.userName)
            .execute()

        tr.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", branchId)
            .bind("orgId", orgId)
            .bind("name", Messages.DefaultBranchName.localize(lang))
            .execute()

        tr.sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", hallId)
            .bind("orgId", orgId)
            .bind("branchId", branchId)
            .bind("name", Messages.DefaultHallName.localize(lang))
            .execute()

        DefaultAttendanceLabels.createFor(orgId, lang)
        DefaultLeadSources.createFor(orgId, lang)

        userSettings.new(userId)
        return AuthUser(id = userId, orgId = orgId, branchId = branchId, employeeId = employeeId, username = request.login)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun verifyCredentials(username: String, password: String, branchId: BranchId): AuthUser {
        val (user, hash) =
            tr.sql(
                """
                SELECT u.id, u.login, u.password_hash, e.id AS employee_id, e.org_id
                FROM users u
                JOIN employees e ON e.user_id = u.id
                WHERE u.login = :username AND e.is_active = true
                """.trimIndent(),
            )
                .bind("username", username)
                .firstOrNull { it.toAuthUser(branchId) to PasswordHash(it.asString("password_hash")) }
                ?: raise(UserNotFound("User with given credentials not found"))

        if (!passwordHasher.verify(password, hash)) {
            raise(UserNotFound("User with given credentials not found"))
        }
        return user
    }

    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun AuthUser.bootstrapContext(lang: Lang, clientIp: String?): EmployeeRequestContext {
        val (currency, employee) =
            context(SystemContexts.forOrg(orgId)) {
                organizations.current().currency to employees.byId(employeeId)
            }
        return EmployeeRequestContext(lang, orgId, currency, id, branchId, username, clientIp, employee)
    }
}

private fun Row.toAuthUser(branchId: BranchId) =
    AuthUser(
        id = asUuid("id").toUserId(),
        orgId = asUuid("org_id").toOrgId(),
        branchId = branchId,
        employeeId = asUuid("employee_id").toEmployeeId(),
        username = asString("login"),
    )

private data class AuthUser(
    override val id: UserId,
    override val orgId: OrgId,
    override val branchId: BranchId,
    override val employeeId: EmployeeId,
    override val username: String,
) : AuthenticatedUser

/** Ошибки, возникающие при регистрации нового пользователя. */
sealed class SignUpError : DomainError {
    /** Логин уже занят другим пользователем. */
    data object UserAlreadyRegistered : SignUpError() {
        override val code = "USER_ALREADY_REGISTERED"
        override val message = "Пользователь с таким логином уже зарегистрирован"
    }
}
