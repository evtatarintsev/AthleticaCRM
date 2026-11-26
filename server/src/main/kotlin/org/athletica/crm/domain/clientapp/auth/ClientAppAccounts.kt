package org.athletica.crm.domain.clientapp.auth

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.toClientAppAccountId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.time.Instant

/** Аккаунты кабинета: один действующий аккаунт на номер во всех организациях. */
interface ClientAppAccounts {
    /** Действующий (не удалённый) аккаунт номера [phone]; `null` — с этим номером ещё не входили. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun byPhone(phone: PhoneDigits): ClientAppAccount?

    /** Действующий (не удалённый) аккаунт [id]; `null` — аккаунт удалён или не существует. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: ClientAppAccountId): ClientAppAccount?

    /** Создаёт аккаунт номера [phone], принявший условия версии [termsVersion] в момент [now]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun new(phone: PhoneDigits, termsVersion: String, now: Instant): ClientAppAccountId

    /** Запоминает, что аккаунт [id] принял условия версии [termsVersion] в момент [now]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun acceptTerms(id: ClientAppAccountId, termsVersion: String, now: Instant)
}

/** Аккаунт кабинета. */
data class ClientAppAccount(
    /** Идентификатор аккаунта. */
    val id: ClientAppAccountId,
    /** Номер аккаунта. */
    val phone: PhoneDigits,
    /** Последняя принятая версия условий; `null` — условия не принимались. */
    val termsVersion: String?,
)

/** Реализация [ClientAppAccounts] поверх таблицы `client_app_accounts`. */
class DbClientAppAccounts : ClientAppAccounts {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun byPhone(phone: PhoneDigits): ClientAppAccount? =
        tr.sql("SELECT id, phone_digits, terms_version FROM client_app_accounts WHERE phone_digits = :phone AND deleted_at IS NULL")
            .bind("phone", phone.value)
            .firstOrNull { it.toAccount() }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: ClientAppAccountId): ClientAppAccount? =
        tr.sql("SELECT id, phone_digits, terms_version FROM client_app_accounts WHERE id = :id AND deleted_at IS NULL")
            .bind("id", id)
            .firstOrNull { it.toAccount() }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(phone: PhoneDigits, termsVersion: String, now: Instant): ClientAppAccountId {
        val id = ClientAppAccountId.new()
        tr.sql(
            """
            INSERT INTO client_app_accounts (id, phone_digits, created_at, terms_accepted_at, terms_version)
            VALUES (:id, :phone, :now, :now, :version)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("phone", phone.value)
            .bind("now", now)
            .bind("version", termsVersion)
            .execute()
        return id
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun acceptTerms(id: ClientAppAccountId, termsVersion: String, now: Instant) {
        tr.sql("UPDATE client_app_accounts SET terms_accepted_at = :now, terms_version = :version WHERE id = :id")
            .bind("id", id)
            .bind("now", now)
            .bind("version", termsVersion)
            .execute()
    }

    /** Аккаунт из строки `client_app_accounts`. */
    private fun Row.toAccount() =
        ClientAppAccount(
            asUuid("id").toClientAppAccountId(),
            checkNotNull(PhoneDigits.from(asString("phone_digits"))),
            asStringOrNull("terms_version"),
        )
}
