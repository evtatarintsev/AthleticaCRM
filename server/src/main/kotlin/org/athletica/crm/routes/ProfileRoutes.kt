package org.athletica.crm.routes

import arrow.fx.coroutines.parZip
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.OrgInfo
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.api.schemas.auth.ResendEmailConfirmationResponse
import org.athletica.crm.api.schemas.auth.ResendEmailStatus
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.auth.Auth
import org.athletica.crm.domain.auth.EmailVerifications
import org.athletica.crm.domain.auth.ResendResult
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.read.auth.profile
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты профиля:
 * GET  /auth/me               — возвращает данные текущего авторизованного пользователя.
 * POST /auth/me/update        — обновляет имя и аватар.
 * POST /auth/me/change-password — меняет пароль (требует старый пароль).
 * [demo] — признак демо-режима экземпляра, который `/auth/me` сообщает клиенту.
 */
context(db: Database)
fun RouteWithContext.profileRoutes(
    auth: Auth,
    organizations: Organizations,
    orgBalances: OrgBalances,
    emailVerifications: EmailVerifications,
    demo: Boolean,
) {
    get<Unit, AuthMeResponse>("/auth/me") {
        val user = profile()
        val info =
            db.transaction {
                parZip(
                    { orgBalances.current() },
                    { organizations.current() },
                ) { balance, org ->
                    OrgInfo(org.name, balance.totalAmount, org.timezone)
                }
            }

        AuthMeResponse(
            id = user.id,
            employeeId = user.employeeId,
            username = user.username,
            name = user.name,
            avatarId = user.avatarId,
            orgInfo = info,
            currentBranch = BranchDetailResponse(user.currentBranchId, user.currentBranchName),
            permissions = grantedPermissions(),
            emailVerificationPending = db.transaction { emailVerifications.pending(user.id) },
            demo = demo,
        )
    }

    post<UpdateMeRequest, Unit>("/auth/me/update") { request ->
        db.transaction {
            auth.updateMe(request.name, request.avatarId)
        }
    }

    post<ChangePasswordRequest, Unit>("/auth/me/change-password") { request ->
        db.transaction {
            auth.changePassword(request.oldPassword, request.newPassword)
        }
    }

    post<Unit, ResendEmailConfirmationResponse>("/auth/me/resend-email-confirmation") {
        db.transaction { emailVerifications.resend() }.toResponse()
    }
}

/** Права, эффективно выданные сотруднику из [ctx] (роли и точечные правки). */
context(ctx: EmployeeRequestContext)
private fun grantedPermissions(): Set<UserPermission> = UserPermission.entries.filterTo(mutableSetOf()) { ctx.hasPermission(it) }

/** Ответ API на итог повторной отправки письма подтверждения. */
private fun ResendResult.toResponse(): ResendEmailConfirmationResponse =
    when (this) {
        is ResendResult.Queued -> ResendEmailConfirmationResponse(ResendEmailStatus.QUEUED, retryAfterSeconds)
        ResendResult.AlreadyConfirmed -> ResendEmailConfirmationResponse(ResendEmailStatus.ALREADY_CONFIRMED, 0)
        is ResendResult.RateLimited -> ResendEmailConfirmationResponse(ResendEmailStatus.RATE_LIMITED, retryAfterSeconds)
    }
