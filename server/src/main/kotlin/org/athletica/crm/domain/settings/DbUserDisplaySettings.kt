package org.athletica.crm.domain.settings

import arrow.core.raise.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/**
 * Настройки отображения пользователя — JSON-документ, структуру которого задаёт клиент.
 * Домен хранит документ целиком и не разбирает его.
 */
interface UserDisplaySettings {
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun new(userId: UserId)

    /** JSON-документ настроек текущего пользователя; null — настройки ещё не сохранялись. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun get(): String?

    /** Сохраняет JSON-документ [settings] как настройки текущего пользователя. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(settings: String)
}

/** Настройки отображения в таблице `user_display_settings`. */
class DbUserDisplaySettings : UserDisplaySettings {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(userId: UserId) {
        tr.sql(
            """
            INSERT INTO user_display_settings (user_id, settings) VALUES (:userId, '{}')
            ON CONFLICT DO NOTHING
        """,
        ).bind("userId", userId).execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun get(): String? =
        tr.sql(
            "SELECT settings FROM user_display_settings WHERE user_id = :userId",
        )
            .bind("userId", ctx.userId)
            .firstOrNull { row -> row.asString("settings") }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save(settings: String) {
        tr.sql(
            """
            INSERT INTO user_display_settings (user_id, settings) VALUES (:userId, :settings::jsonb)
            ON CONFLICT (user_id) DO UPDATE SET settings = EXCLUDED.settings
            """,
        )
            .bind("userId", ctx.userId)
            .bind("settings", settings)
            .execute()
    }
}
