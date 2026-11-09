package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction

class AuditAuth(private val delegate: Auth, private val audit: AuditLog) : Auth by delegate {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun login(
        username: String,
        password: String,
        branchId: BranchId,
        lang: Lang,
        clientIp: String?,
    ) = delegate.login(username, password, branchId, lang, clientIp).also { user ->
        audit.log(
            AuditEvent(
                orgId = user.orgId,
                userId = user.id,
                username = user.username,
                actionType = AuditActionType.AUTH_LOGIN,
                ipAddress = clientIp,
            ),
        )
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun signup(request: SignUpRequest, lang: Lang, clientIp: String?) =
        delegate.signup(request, lang, clientIp).also { user ->
            audit.log(
                AuditEvent(
                    orgId = user.orgId,
                    userId = user.id,
                    username = user.username,
                    actionType = AuditActionType.AUTH_SIGNUP,
                    ipAddress = clientIp,
                ),
            )
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun logout() {
        delegate.logout()
        audit.log(AuditEvent(ctx, AuditActionType.AUTH_LOGOUT))
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun changePassword(request: ChangePasswordRequest) {
        delegate.changePassword(request)
        audit.log(AuditEvent(ctx, AuditActionType.AUTH_CHANGE_PASSWORD))
    }
}
