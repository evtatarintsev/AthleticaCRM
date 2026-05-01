package org.athletica.crm.routes

import arrow.core.raise.context.raise
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionDto
import org.athletica.crm.api.schemas.customfields.SaveCustomFieldsRequest
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.customfields.getCustomFields
import org.athletica.crm.usecases.customfields.saveCustomFields

/**
 * Регистрирует маршруты для управления определениями кастомных полей.
 * `orgId` берётся из [RequestContext]; `entityType` — из query-параметра `entityType`.
 */
context(db: Database)
fun RouteWithContext.customFieldsRoutes(definitions: CustomFieldDefinitions) {
    route("/custom-fields") {
        get<List<CustomFieldDefinitionDto>>("/list") { call ->
            val entityType =
                call.request.queryParameters["entityType"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterEntityType.localize()))
            db.transaction {
                getCustomFields(definitions, entityType)
            }
        }

        post<SaveCustomFieldsRequest, List<CustomFieldDefinitionDto>>("/save") { request, call ->
            val entityType =
                call.request.queryParameters["entityType"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterEntityType.localize()))
            db.transaction {
                saveCustomFields(definitions, entityType, request.fields)
            }
        }
    }
}
