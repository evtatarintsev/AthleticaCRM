package org.athletica.crm.api.schemas.clientapp

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import kotlin.time.Instant

/**
 * Неподключённое приглашение организации [orgId] с названием [orgName]: момент последнего приглашения
 * [invitedAt] и число клиентов [clientCount] с номером аккаунта. Данных клиентов нет до подключения.
 */
@Serializable
data class ClientAppInvitationSchema(
    val orgId: OrgId,
    val orgName: String,
    val invitedAt: Instant,
    val clientCount: Int,
)

/** Неподключённые приглашения аккаунта [invitations], самые свежие первыми. */
@Serializable
data class ClientAppInvitationsResponse(
    val invitations: List<ClientAppInvitationSchema>,
)

/** Ответ на приглашение организации [orgId]: «Подключить» или «Это не я». */
@Serializable
data class ClientAppInvitationRequest(
    val orgId: OrgId,
)

/** Подключённый профиль: клиент [clientId] с именем [clientName] организации [orgId] с названием [orgName]. */
@Serializable
data class ClientAppProfileSchema(
    val orgId: OrgId,
    val orgName: String,
    val clientId: ClientId,
    val clientName: String,
)

/**
 * Подключённые профили аккаунта [profiles], по организациям и именам, и число организаций
 * с неподключёнными приглашениями [newInvitations].
 */
@Serializable
data class ClientAppProfilesResponse(
    val profiles: List<ClientAppProfileSchema>,
    val newInvitations: Int,
)

/** Профиль, к которому относится запрос: клиент [clientId] организации [orgId]. */
@Serializable
data class ClientAppProfileRequest(
    val orgId: OrgId,
    val clientId: ClientId,
)

/** Главная профиля: имя клиента [clientName] для приветствия и название организации [orgName]. */
@Serializable
data class ClientAppHomeResponse(
    val clientName: String,
    val orgName: String,
)
