package org.athletica.crm.usecases.org

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asString
import org.athletica.crm.i18n.Messages

/**
 * Возвращает основные настройки организации текущего пользователя из [ctx].
 */
context(db: Database, ctx: RequestContext)
suspend fun getOrgSettings(): Either<CommonDomainError, OrgSettingsResponse> =
    either {
        db
            .sql("SELECT name, timezone FROM organizations WHERE id = :orgId")
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                OrgSettingsResponse(
                    name = row.asString("name"),
                    timezone = row.asString("timezone"),
                )
            } ?: raise(CommonDomainError("ORG_NOT_FOUND", Messages.OrgNotFound.localize()))
    }
