package org.athletica.crm.routes

import org.athletica.crm.api.schemas.customfields.CustomFieldsListRequest
import org.athletica.crm.api.schemas.customfields.SaveCustomFieldsRequest
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты для управления определениями кастомных полей.
 * `orgId` берётся из [RequestContext]; `entityType` — из query-параметра `entityType`.
 */
context(db: Database)
fun RouteWithContext.customFieldsRoutes(definitions: CustomFieldDefinitions) {
    route("/custom-fields") {
        get<CustomFieldsListRequest, List<CustomFieldDefinition>>("/list") { request ->
            db.transaction {
                definitions.all(request.entityType)
            }
        }

        post<SaveCustomFieldsRequest, List<CustomFieldDefinition>>("/save") { request ->
            db.transaction {
                definitions.saveAll(request.entityType, request.fields)
                definitions.all(request.entityType)
            }
        }
    }
}
