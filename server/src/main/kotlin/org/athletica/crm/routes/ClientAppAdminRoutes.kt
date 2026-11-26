package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.clientappadmin.ClientAppBulkInviteRequest
import org.athletica.crm.api.schemas.clientappadmin.ClientAppBulkInviteResponse
import org.athletica.crm.api.schemas.clientappadmin.ClientAppClientAccessResponse
import org.athletica.crm.api.schemas.clientappadmin.ClientAppClientRequest
import org.athletica.crm.api.schemas.clientappadmin.ClientAppInvitePreviewResponse
import org.athletica.crm.api.schemas.clientappadmin.ClientAppInviteResponse
import org.athletica.crm.api.schemas.clientappadmin.ClientAppPhoneAccessSchema
import org.athletica.crm.api.schemas.clientappadmin.ClientAppPhoneRequest
import org.athletica.crm.api.schemas.clientappadmin.ClientAppSettingsResponse
import org.athletica.crm.api.schemas.clientappadmin.SetClientAppEnabledRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.requireManageClientApp
import org.athletica.crm.domain.clientapp.ClientAppAccess
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.domain.clientapp.ClientAppSettings
import org.athletica.crm.domain.clientapp.InvitedClientAppAccess
import org.athletica.crm.domain.clientapp.StoredClientAppAccess
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.usecases.clientapp.ClientAppAdmin

/**
 * Маршруты управления клиентским кабинетом `/client-app-admin/...` для сотрудников: настройки и массовое
 * приглашение требуют права `CAN_MANAGE_CLIENT_APP`, блок карточки клиента и действия с номером — любой
 * сотрудник организации. Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.clientAppAdminRoutes(
    admin: ClientAppAdmin,
    settings: ClientAppSettings,
    accesses: ClientAppAccesses,
    views: ReadViews,
) {
    route("/client-app-admin") {
        get<ClientAppSettingsResponse>("/settings") {
            requireManageClientApp()
            db.transaction { settingsResponse(admin, settings) }
        }

        post<SetClientAppEnabledRequest, ClientAppSettingsResponse>("/set-enabled") { request ->
            db.transaction {
                admin.setEnabled(request.enabled)
                settingsResponse(admin, settings)
            }
        }

        post<ClientAppBulkInviteRequest, ClientAppInvitePreviewResponse>("/invite-preview") { request ->
            requireManageClientApp()
            db.transaction { views.clientAppInvitePreview.preview(request.groupIds) }
        }

        post<ClientAppBulkInviteRequest, ClientAppBulkInviteResponse>("/bulk-invite") { request ->
            db.transaction {
                val result = admin.bulkInvite(request.groupIds)
                ClientAppBulkInviteResponse(result.invited, result.notified)
            }
        }

        get<ClientAppClientRequest, ClientAppClientAccessResponse>("/client") { request ->
            db.transaction {
                ClientAppClientAccessResponse(settings.enabled(), accesses.byClient(request.clientId).map { it.toSchema() })
            }
        }

        post<ClientAppClientRequest, ClientAppInviteResponse>("/invite") { request ->
            db.transaction { ClientAppInviteResponse(admin.invite(request.clientId)) }
        }

        post<ClientAppPhoneRequest, ClientAppInviteResponse>("/remind") { request ->
            db.transaction { ClientAppInviteResponse(admin.remind(request.clientId, phone(request.phone))) }
        }

        post<ClientAppPhoneRequest, Unit>("/disable") { request ->
            db.transaction { admin.disable(request.clientId, phone(request.phone)) }
        }
    }
}

/** Настройки кабинета организации запроса. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
private suspend fun settingsResponse(
    admin: ClientAppAdmin,
    settings: ClientAppSettings,
) = ClientAppSettingsResponse(settings.enabled(), admin.codeTransportAvailable())

/** Номер из запроса; не номер — `INVALID_PHONE`. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
private fun phone(value: String): PhoneDigits = PhoneDigits.from(value) ?: raise(CommonDomainError("INVALID_PHONE", Messages.ClientAppInvalidPhone.localize()))

/** Доступ номера для карточки клиента. */
private fun ClientAppAccess.toSchema(): ClientAppPhoneAccessSchema {
    val stored = this as? StoredClientAppAccess
    return ClientAppPhoneAccessSchema(
        phone = phone.value,
        status = status,
        invitedAt = stored?.invitedAt,
        inviteChannel = stored?.inviteChannel,
        statusChangedAt = stored?.statusChangedAt,
        lastSeenAt = stored?.lastSeenAt,
        remindAvailableAt = (this as? InvitedClientAppAccess)?.remindAvailableAt,
    )
}
