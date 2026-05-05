package org.athletica.crm.domain.settings

import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.settings.DisplaySettings
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.storage.Transaction

interface UserDisplaySettings {
    context(tr: Transaction)
    suspend fun new(userId: UserId)

    context(ctx: RequestContext, tr: Transaction)
    suspend fun get(): DisplaySettings

    context(ctx: RequestContext, tr: Transaction)
    suspend fun save(settings: DisplaySettings)
}

class DbUserDisplaySettings : UserDisplaySettings {
    context(tr: Transaction)
    override suspend fun new(userId: UserId) {
        tr.sql(
            """
            INSERT INTO user_display_settings (user_id, settings) VALUES (:userId, '{}')
            ON CONFLICT DO NOTHING
        """,
        ).bind("userId", userId).execute()
    }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun get(): DisplaySettings {
        val json =
            tr.sql(
                "SELECT settings FROM user_display_settings WHERE user_id = :userId",
            )
                .bind("userId", ctx.userId)
                .firstOrNull { row -> row.get("settings", String::class.java) }
        return json?.let { Json.decodeFromString<DisplaySettings>(it) } ?: DisplaySettings()
    }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun save(settings: DisplaySettings) {
        tr.sql(
            """
            INSERT INTO user_display_settings (user_id, settings) VALUES (:userId, :settings::jsonb)
            ON CONFLICT (user_id) DO UPDATE SET settings = EXCLUDED.settings
            """,
        )
            .bind("userId", ctx.userId)
            .bind("settings", Json.encodeToString(settings))
            .execute()
    }
}
