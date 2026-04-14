package org.athletica.crm.usecases.org

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages

/**
 * Обновляет название и часовой пояс организации текущего пользователя из [ctx].
 * Возвращает обновлённые настройки.
 */
context(db: Database, ctx: RequestContext)
suspend fun updateOrgSettings(request: UpdateOrgSettingsRequest): Either<CommonDomainError, OrgSettingsResponse> =
    either {
        if (request.name.isBlank()) {
            raise(CommonDomainError("VALIDATION_ERROR", Messages.OrgNameBlank.localize()))
        }
        db
            .sql(
                """
                UPDATE organizations
                SET name = :name, timezone = :timezone
                WHERE id = :orgId
                """.trimIndent(),
            )
            .bind("name", request.name.trim())
            .bind("timezone", request.timezone)
            .bind("orgId", ctx.orgId)
            .execute()
        OrgSettingsResponse(name = request.name.trim(), timezone = request.timezone)
    }
