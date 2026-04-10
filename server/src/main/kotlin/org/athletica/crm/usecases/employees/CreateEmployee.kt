package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Создаёт нового сотрудника по данным [request].
 *
 * Email обязателен: на его основе создаётся учётная запись пользователя (`login = email`).
 * Пароль генерируется случайным образом и хешируется — сотрудник не может войти до тех пор,
 * пока администратор не отправит ему доступ через [sendEmployeeAccess].
 *
 * Создание `users` и `employees` выполняется атомарно в одной транзакции.
 *
 * Возможные ошибки:
 * - `EMPLOYEE_EMAIL_REQUIRED` — поле `email` не передано в запросе.
 * - `EMPLOYEE_EMAIL_IN_USE` — пользователь с таким email уже зарегистрирован.
 * - `EMPLOYEE_ALREADY_EXISTS` — сотрудник с таким `id` уже существует.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog, passwordHasher: PasswordHasher)
suspend fun createEmployee(request: CreateEmployeeRequest): Either<CommonDomainError, EmployeeListItem> =
    either {
        val email =
            request.email
                ?: raise(CommonDomainError("EMPLOYEE_EMAIL_REQUIRED", Messages.EmployeeEmailRequired.localize()))

        val now = java.time.Instant.now()
        val userId = UserId.new()
        // Locked placeholder password — employee cannot log in until access is explicitly sent
        val lockedHash = passwordHasher.hash(Uuid.random().toString())

        try {
            db.transaction {
                sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
                    .bind("userId", userId)
                    .bind("login", email)
                    .bind("hash", lockedHash.value)
                    .execute()

                sql(
                    """
                    INSERT INTO employees (id, user_id, org_id, name, avatar_id, phone_no, email, is_active)
                    VALUES (:id, :userId, :orgId, :name, :avatarId, :phoneNo, :email, false)
                    """.trimIndent(),
                )
                    .bind("id", request.id)
                    .bind("userId", userId)
                    .bind("orgId", ctx.orgId.value)
                    .bind("name", request.name)
                    .bind("avatarId", request.avatarId?.toJavaUuid())
                    .bind("phoneNo", request.phoneNo)
                    .bind("email", email)
                    .execute()
            }
        } catch (e: R2dbcDataIntegrityViolationException) {
            when {
                e.message?.contains("users_login_key") == true ->
                    raise(CommonDomainError("EMPLOYEE_EMAIL_IN_USE", Messages.EmployeeEmailInUse.localize()))
                e.message?.contains("employees_pkey") == true ->
                    raise(CommonDomainError("EMPLOYEE_ALREADY_EXISTS", Messages.EmployeeAlreadyExists.localize()))
                else -> throw e
            }
        }

        EmployeeListItem(
            id = request.id,
            name = request.name,
            avatarId = request.avatarId,
            isOwner = false,
            isActive = false,
            joinedAt = now.toKotlinInstant(),
            roles = emptyList<EmployeeRole>(),
            phoneNo = request.phoneNo,
            email = email,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание сотрудника: тип сущности `"employee"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: EmployeeListItem) = logCreate("employee", result.id, Json.encodeToString(result))
