package org.athletica.crm.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toJson
import org.athletica.crm.domain.clientapp.codes.CodeChannel
import org.athletica.crm.domain.clientapp.codes.CodeDeliveryError
import org.athletica.crm.domain.clientapp.codes.VerificationCode
import org.athletica.crm.domain.clientapp.codes.VerificationCodeSender
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Данные кабинета клиента для тестов — прямыми SQL-вставками. */
object ClientAppFixture {
    /** SMS-канал для тестов. */
    val SMS_CONFIG = ChannelConfig.SmsAeroSms(email = "sms@example.com", apiKey = "key", sign = "Club")

    /**
     * Организация [name]; [cabinet] — кабинет включён, [gateway] — включён Telegram Gateway,
     * [sms] — включён SMS-канал.
     */
    context(raise: Raise<AppError>)
    suspend fun org(
        name: String,
        cabinet: Boolean = true,
        gateway: Boolean = false,
        sms: Boolean = false,
    ): OrgId {
        val id = OrgId.new()
        TestPostgres.db.sql("INSERT INTO organizations (id, name, client_app_enabled) VALUES (:id, :name, :cabinet)")
            .bind("id", id).bind("name", name).bind("cabinet", cabinet).execute()
        if (gateway) {
            TestPostgres.db.sql("INSERT INTO telegram_gateway_integrations (org_id, token, enabled) VALUES (:id, 'token', true)").bind("id", id).execute()
        }
        if (sms) {
            TestPostgres.db.sql("INSERT INTO channel_integrations (id, org_id, name, config, enabled) VALUES (:cid, :id, 'SMS', :config::jsonb, true)")
                .bind("cid", Uuid.generateV7()).bind("id", id).bind("config", SMS_CONFIG.toJson()).execute()
        }
        return id
    }

    /**
     * Клиент [name] организации [orgId] с телефоном [phone] в контактах и доступом в статусе [status],
     * приглашённым в [invitedAt].
     */
    context(raise: Raise<AppError>)
    suspend fun client(
        orgId: OrgId,
        phone: String,
        status: ClientAppAccessStatus? = ClientAppAccessStatus.INVITED,
        invitedAt: Instant = Clock.System.now(),
        name: String = "Иван",
    ): ClientId {
        val id = ClientId.new()
        TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", id).bind("orgId", orgId).bind("name", name).execute()
        TestPostgres.db.sql("INSERT INTO client_contacts (id, org_id, client_id, type, value, position, is_primary) VALUES (:cid, :orgId, :id, 'PHONE', :phone, 0, true)")
            .bind("cid", Uuid.generateV7()).bind("orgId", orgId).bind("id", id).bind("phone", phone).execute()
        if (status != null) {
            TestPostgres.db.sql(
                """
                INSERT INTO client_app_access (id, org_id, client_id, phone_digits, status, invited_at, status_changed_at)
                VALUES (:aid, :orgId, :id, :digits, :status, :at, :at)
                """.trimIndent(),
            )
                .bind("aid", Uuid.generateV7()).bind("orgId", orgId).bind("id", id)
                .bind("digits", checkNotNull(PhoneDigits.from(phone)).value).bind("status", status.name).bind("at", invitedAt)
                .execute()
        }
        return id
    }

    /** Аккаунт кабинета номера [phone], принявший условия. */
    context(raise: Raise<AppError>)
    suspend fun account(phone: String): ClientAppAccountId {
        val id = ClientAppAccountId.new()
        TestPostgres.db.sql("INSERT INTO client_app_accounts (id, phone_digits, created_at, terms_accepted_at, terms_version) VALUES (:id, :phone, now(), now(), 'v1')")
            .bind("id", id).bind("phone", checkNotNull(PhoneDigits.from(phone)).value).execute()
        return id
    }

    /** Контекст аккаунта [id] с новым сеансом. */
    fun accountContext(id: ClientAppAccountId) = ClientAccountContext(id, ClientAppSessionId.new(), Lang.RU)
}

/** Отправитель-заглушка способа [channel]: запоминает отправки и отвечает ошибкой [error], если она задана. */
class RecordingSender(
    override val channel: CodeChannel,
    private val error: CodeDeliveryError? = null,
) : VerificationCodeSender {
    /** Номера, на которые пытались отправить код. */
    val sent = mutableListOf<PhoneDigits>()

    context(raise: Raise<CodeDeliveryError>)
    override suspend fun send(
        phone: PhoneDigits,
        code: VerificationCode,
    ) {
        sent += phone
        error?.let { raise.raise(it) }
    }
}
