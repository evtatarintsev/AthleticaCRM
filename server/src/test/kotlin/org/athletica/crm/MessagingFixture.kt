package org.athletica.crm

import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString

/** Вставка данных переписки напрямую в БД для тестов каналов, входящих и триажа. */
object MessagingFixture {
    /** Конфиг Telegram-интеграции с токеном [token] в виде JSON колонки `config`. */
    fun telegramConfig(token: String = "${System.nanoTime()}:secret", username: String? = null): String =
        if (username == null) {
            """{"type":"telegram_bot","botToken":"$token"}"""
        } else {
            """{"type":"telegram_bot","botToken":"$token","botUsername":"$username"}"""
        }

    /** Создаёт организацию [orgId]. */
    suspend fun insertOrg(orgId: OrgId, name: String = "Org") {
        TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId).bind("name", name).execute()
    }

    /** Создаёт клиента [clientId] организации [orgId]. */
    suspend fun insertClient(orgId: OrgId, clientId: ClientId = ClientId.new(), name: String = "Клиент"): ClientId {
        TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name).execute()
        return clientId
    }

    /** Добавляет клиенту [clientId] телефон [phone] в исходном формате. */
    suspend fun insertPhone(orgId: OrgId, clientId: ClientId, phone: String) {
        TestPostgres.db.sql(
            """
            INSERT INTO client_contacts (id, org_id, client_id, type, value)
            VALUES (:id, :orgId, :clientId, 'PHONE', :value)
            """.trimIndent(),
        ).bind("id", ClientContactId.new()).bind("orgId", orgId).bind("clientId", clientId).bind("value", phone).execute()
    }

    /** Создаёт интеграцию канала организации [orgId] с конфигом [config]. */
    suspend fun insertIntegration(
        orgId: OrgId,
        config: String = telegramConfig(),
        name: String = "Бот",
        enabled: Boolean = true,
        id: ChannelIntegrationId = ChannelIntegrationId.new(),
    ): ChannelIntegrationId {
        TestPostgres.db.sql(
            """
            INSERT INTO channel_integrations (id, org_id, name, config, enabled)
            VALUES (:id, :orgId, :name, :config::jsonb, :enabled)
            """.trimIndent(),
        ).bind("id", id).bind("orgId", orgId).bind("name", name).bind("config", config).bind("enabled", enabled).execute()
        return id
    }

    /** Привязывает адрес [address] интеграции [integrationId] к клиенту [clientId]. */
    suspend fun insertBinding(
        orgId: OrgId,
        integrationId: ChannelIntegrationId,
        clientId: ClientId,
        address: String,
    ) {
        TestPostgres.db.sql(
            """
            INSERT INTO channel_bindings (id, org_id, integration_id, client_id, address)
            VALUES (:id, :orgId, :integrationId, :clientId, :address)
            """.trimIndent(),
        ).bind("id", ChannelBindingId.new()).bind("orgId", orgId).bind("integrationId", integrationId)
            .bind("clientId", clientId).bind("address", address).execute()
    }

    /** Тексты входящих сообщений беседы клиента [clientId] в порядке времени. */
    suspend fun inboundBodies(clientId: ClientId): List<String> =
        TestPostgres.db.sql(
            """
            SELECT m.body
            FROM messages m JOIN conversations c ON c.id = m.conversation_id
            WHERE c.client_id = :clientId AND m.direction = 'INBOUND'
            ORDER BY m.created_at, m.id
            """.trimIndent(),
        ).bind("clientId", clientId).list { row -> row.asString("body") }

    /** Число неразобранных входящих интеграции [integrationId]. */
    suspend fun unresolvedCount(integrationId: ChannelIntegrationId): Long =
        TestPostgres.db.sql(
            "SELECT count(*) AS c FROM inbound_receipts WHERE integration_id = :id AND state = 'UNRESOLVED'",
        ).bind("id", integrationId).firstOrNull { row -> row.asLong("c") } ?: 0L

    /** Клиент, к которому привязан адрес [address] интеграции [integrationId]; `null`, если связки нет. */
    suspend fun boundClient(integrationId: ChannelIntegrationId, address: String): String? =
        TestPostgres.db.sql(
            "SELECT client_id::text AS c FROM channel_bindings WHERE integration_id = :id AND address = :address",
        ).bind("id", integrationId).bind("address", address).firstOrNull { row -> row.asString("c") }
}
