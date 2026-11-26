package org.athletica.crm.domain.clientapp.codes

import arrow.core.raise.context.Raise
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Выбор организации, которая платит за код входа на номер. Плательщик — организация с включённым
 * кабинетом, где у номера доступ `приглашён` или `подключён` (клиент не в архиве, номер в контактах);
 * из подходящих берётся организация с самым свежим приглашением.
 */
object CodePayers {
    /** Организация-плательщик с включённым Telegram Gateway; `null` — такой нет. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun telegram(phone: PhoneDigits): OrgId? =
        tr.sql(
            """
            SELECT a.org_id
            $ELIGIBLE_ACCESS
            JOIN telegram_gateway_integrations g ON g.org_id = a.org_id AND g.enabled
            WHERE $ELIGIBLE_WHERE
            GROUP BY a.org_id
            ORDER BY max(a.invited_at) DESC, a.org_id
            LIMIT 1
            """.trimIndent(),
        )
            .bind("phone", phone.value)
            .firstOrNull { it.asUuid("org_id").toOrgId() }

    /** Организация-плательщик с включённым каналом, который адресует по телефону; `null` — такой нет. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun sms(phone: PhoneDigits): SmsPayer? =
        tr.sql(
            """
            SELECT a.org_id, o.name, ci.config::text AS config
            $ELIGIBLE_ACCESS
            JOIN channel_integrations ci ON ci.org_id = a.org_id AND ci.enabled
            WHERE $ELIGIBLE_WHERE
            GROUP BY a.org_id, o.name, ci.id, ci.config::text
            ORDER BY max(a.invited_at) DESC, a.org_id, ci.id
            """.trimIndent(),
        )
            .bind("phone", phone.value)
            .list { SmsPayer(it.asUuid("org_id").toOrgId(), it.asString("name"), it.asString("config").toChannelConfig()) }
            .firstOrNull { it.config.addressing.accepts(ContactType.PHONE) }

    /** Доступы номера, которые делают организацию возможным плательщиком. */
    private const val ELIGIBLE_ACCESS = """
        FROM client_app_access a
        JOIN organizations o ON o.id = a.org_id AND o.client_app_enabled
        JOIN clients c ON c.id = a.client_id AND c.org_id = a.org_id AND c.state = 'ACTIVE'
    """

    /** Условие на доступы [ELIGIBLE_ACCESS]: номер запроса, статус и номер всё ещё в контактах. */
    private const val ELIGIBLE_WHERE = """
        a.phone_digits = :phone AND a.status IN ('INVITED', 'CONNECTED')
        AND EXISTS (
            SELECT 1 FROM client_contacts cc
            WHERE cc.org_id = a.org_id AND cc.client_id = a.client_id AND cc.type = 'PHONE'
              AND normalized_phone(cc.value) = a.phone_digits
        )
    """
}

/** Организация [orgId] с названием [orgName], которая отправит код по SMS-каналу [config]. */
data class SmsPayer(
    val orgId: OrgId,
    val orgName: String,
    val config: ChannelConfig,
)
