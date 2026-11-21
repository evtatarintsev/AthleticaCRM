package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserActor
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Сотрудник организации — агрегат и одновременно актор запроса:
 * ответ «что может сотрудник» даёт [hasPermission]. Устройство прав (роли,
 * персональные выдачи и отзывы) — вход команд [withNew] и `Employees.new`.
 */
interface Employee : UserActor {
    val id: EmployeeId
    val userId: UserId?
    val name: String
    val avatarId: UploadId?
    val isOwner: Boolean

    /** Момент блокировки сотрудника; `null` — не заблокирован. */
    val blockedAt: Instant?

    /** Может ли сотрудник работать в системе: логин выдан и сотрудник не заблокирован. */
    val isActive: Boolean get() = userId != null && blockedAt == null
    val joinedAt: Instant
    val phoneNo: String?
    val email: EmailAddress?
    val roles: List<EmployeeRole>

    /** Доступ к филиалам организации: ко всем (включая будущие) либо к явному набору. */
    val availableBranches: EmployeeBranchAccess

    /** Есть ли доступ к филиалу организации, независимо от способа его назначения. */
    fun hasAccessTo(branchId: BranchId): Boolean = availableBranches.contains(branchId)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save()

    /** Выдаёт сотруднику логин [email] с паролем [password]; повторная выдача отклоняется. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun invite(email: EmailAddress, password: String)

    /**
     * Блокирует сотрудника и завершает все его сессии; владельца и самого себя заблокировать нельзя,
     * повторная блокировка ничего не меняет.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun block()

    /**
     * Завершает сессию входа [sessionId] сотрудника. Правила те же, что у [block]: сессии владельца
     * и свои через сотрудника не завершаются; чужая или несуществующая сессия — ошибка.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun terminateSession(sessionId: AuthSessionId)

    /** Снимает блокировку с сотрудника. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun unblock()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun withNew(
        newName: NonBlankName,
        newPermissions: EmployeePermissionSelection,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
        newAvailableBranches: EmployeeBranchAccess,
    ): Employee
}

data class EmployeePermissionSelection(
    val roleIds: List<Uuid> = emptyList(),
    val grantedPermissions: Set<UserPermission> = emptySet(),
    val revokedPermissions: Set<UserPermission> = emptySet(),
)

data class EmployeePermission(
    val roles: List<EmployeeRole>,
    val grantedPermissions: Set<UserPermission>,
    val revokedPermissions: Set<UserPermission>,
) : UserActor {
    constructor() : this(emptyList(), emptySet(), emptySet())

    override fun hasPermission(p: UserPermission): Boolean {
        if (p in revokedPermissions) return false
        if (p in grantedPermissions) return true
        return roles.any { p in it.permissions }
    }
}

/**
 * Доступ сотрудника к филиалам организации.
 * Делает невозможным некорректные комбинации `(allBranchesAccess: Boolean, branchIds: List)`:
 * - [All] — доступ ко всем филиалам организации, включая создаваемые в будущем.
 * - [Selected] — доступ только к перечисленным филиалам.
 */
sealed class EmployeeBranchAccess {
    /** Возвращает true, если у сотрудника есть доступ к филиалу [branchId]. */
    abstract fun contains(branchId: BranchId): Boolean

    object All : EmployeeBranchAccess() {
        override fun contains(branchId: BranchId): Boolean = true
    }

    data class Selected(val ids: List<BranchId>) : EmployeeBranchAccess() {
        override fun contains(branchId: BranchId): Boolean = branchId in ids
    }
}
