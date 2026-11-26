package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import io.r2dbc.spi.Row
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientAppAccessId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toClientAppAccessId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Реализация репозитория доступов к кабинету клиента на PostgreSQL. */
class DbClientAppAccesses : ClientAppAccesses {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byClient(clientId: ClientId): List<ClientAppAccess> =
        tr.sql(
            """
            SELECT :orgId AS org_id, :clientId AS client_id, p.digits AS phone_digits,
                   a.id, a.status, a.invited_at, a.status_changed_at, a.last_seen_at
            FROM (
                SELECT phone_digits(value) AS digits, min(position) AS position
                FROM client_contacts
                WHERE org_id = :orgId AND client_id = :clientId AND type = 'PHONE'
                GROUP BY 1
            ) p
            LEFT JOIN client_app_access a
                ON a.org_id = :orgId AND a.client_id = :clientId AND a.phone_digits = p.digits
            WHERE p.digits <> ''
            ORDER BY p.position
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .list { it.toAccess() }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun byPhone(phone: PhoneDigits): List<StoredClientAppAccess> =
        tr.sql(
            """
            SELECT a.org_id, a.client_id, a.phone_digits, a.id, a.status, a.invited_at, a.status_changed_at, a.last_seen_at
            FROM client_app_access a
            JOIN organizations o ON o.id = a.org_id AND o.client_app_enabled
            JOIN clients c ON c.id = a.client_id AND c.org_id = a.org_id AND c.state = 'ACTIVE'
            WHERE a.phone_digits = :phone
            ORDER BY a.invited_at DESC, a.id
            """.trimIndent(),
        )
            .bind("phone", phone.value)
            .list { it.toAccess() as StoredClientAppAccess }

    private fun Row.toAccess(): ClientAppAccess {
        val orgId = asUuid("org_id").toOrgId()
        val clientId = asUuid("client_id").toClientId()
        val phone = checkNotNull(PhoneDigits.from(asString("phone_digits")))
        val id = asUuidOrNull("id")?.toClientAppAccessId() ?: return DbNotInvited(orgId, clientId, phone)
        val stored = StoredFields(id, orgId, clientId, phone, asInstant("invited_at"), asInstant("status_changed_at"), asInstantOrNull("last_seen_at"))
        return when (ClientAppAccessStatus.valueOf(asString("status"))) {
            ClientAppAccessStatus.INVITED -> DbInvited(stored)
            ClientAppAccessStatus.CONNECTED -> DbConnected(stored)
            ClientAppAccessStatus.NOT_ME -> DbNotMe(stored)
            ClientAppAccessStatus.DISABLED -> DbDisabled(stored)
            ClientAppAccessStatus.NOT_INVITED -> error("Статус NOT_INVITED не хранится в БД: доступ $id")
        }
    }
}

/** Поля хранимого доступа, общие для всех его статусов. */
private data class StoredFields(
    override val id: ClientAppAccessId,
    override val orgId: OrgId,
    override val clientId: ClientId,
    override val phone: PhoneDigits,
    override val invitedAt: Instant,
    override val statusChangedAt: Instant,
    override val lastSeenAt: Instant?,
) : ClientAppAccessRecord

/** Доступ без приглашения: строки ещё нет. */
private data class DbNotInvited(
    override val orgId: OrgId,
    override val clientId: ClientId,
    override val phone: PhoneDigits,
) : NotInvitedClientAppAccess {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun invite(): InvitedClientAppAccess {
        val id = ClientAppAccessId.new()
        val at = Clock.System.now()
        tr.sql(
            """
            INSERT INTO client_app_access (id, org_id, client_id, phone_digits, status, invited_at, status_changed_at)
            VALUES (:id, :orgId, :clientId, :phone, 'INVITED', :at, :at)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("clientId", clientId)
            .bind("phone", phone.value)
            .bind("at", at)
            .onViolation("uq_client_app_access_org_client_phone") { accessChanged() }
            .execute()
        return DbInvited(StoredFields(id, orgId, clientId, phone, at, at, null))
    }
}

/**
 * Переводит доступ [this] из статуса [from] в [to]; [reinvite] — заодно обновить момент приглашения.
 * Возвращает поля доступа после перехода.
 */
context(tr: Transaction, raise: Raise<AppError>)
private suspend fun StoredFields.moved(
    from: ClientAppAccessStatus,
    to: ClientAppAccessStatus,
    reinvite: Boolean = false,
): StoredFields {
    val at = Clock.System.now()
    val updated =
        tr.sql(
            """
            UPDATE client_app_access
            SET status = :to, status_changed_at = :at,
                invited_at = CASE WHEN :reinvite THEN :at ELSE invited_at END
            WHERE id = :id AND org_id = :orgId AND status = :from
            """.trimIndent(),
        )
            .bind("to", to.name)
            .bind("at", at)
            .bind("reinvite", reinvite)
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("from", from.name)
            .execute()
    ensure(updated == 1L) { accessChanged() }
    return copy(statusChangedAt = at, invitedAt = if (reinvite) at else invitedAt)
}

/** Приглашённый доступ. */
private class DbInvited(private val fields: StoredFields) :
    InvitedClientAppAccess,
    ClientAppAccessRecord by fields {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun connect(): ConnectedClientAppAccess = DbConnected(fields.moved(status, ClientAppAccessStatus.CONNECTED))

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun rejectAsNotMe(): NotMeClientAppAccess = DbNotMe(fields.moved(status, ClientAppAccessStatus.NOT_ME))

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun disable(): DisabledClientAppAccess = DbDisabled(fields.moved(status, ClientAppAccessStatus.DISABLED))
}

/** Подключённый доступ. */
private class DbConnected(private val fields: StoredFields) :
    ConnectedClientAppAccess,
    ClientAppAccessRecord by fields {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun disable(): DisabledClientAppAccess = DbDisabled(fields.moved(status, ClientAppAccessStatus.DISABLED))
}

/** Доступ после «Это не я». */
private class DbNotMe(private val fields: StoredFields) :
    NotMeClientAppAccess,
    ClientAppAccessRecord by fields {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun invite(): InvitedClientAppAccess = DbInvited(fields.moved(status, ClientAppAccessStatus.INVITED, reinvite = true))
}

/** Отключённый доступ. */
private class DbDisabled(private val fields: StoredFields) :
    DisabledClientAppAccess,
    ClientAppAccessRecord by fields {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun invite(): InvitedClientAppAccess = DbInvited(fields.moved(status, ClientAppAccessStatus.INVITED, reinvite = true))
}

/** Статус доступа изменился в другом запросе. */
private fun accessChanged() = CommonDomainError("CLIENT_APP_ACCESS_CHANGED", "Статус доступа изменился, обновите страницу")
