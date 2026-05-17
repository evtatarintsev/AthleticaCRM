package org.athletica.crm.routes

import org.athletica.crm.api.schemas.clients.import.ClientImportCommitRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse
import org.athletica.crm.api.schemas.clients.import.ClientImportParseRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.usecases.clients.import.importClientsCommit
import org.athletica.crm.usecases.clients.import.importClientsParse

/**
 * Регистрирует маршруты импорта клиентов из CSV.
 * Загрузка самого файла идёт через существующий `POST /upload`;
 * здесь — только разбор и применение маппинга к ранее загруженному файлу.
 */
context(db: Database, minio: MinioService)
fun RouteWithContext.clientImportRoutes(
    clients: Clients,
    balances: ClientBalances,
    leadSources: LeadSources,
    definitions: CustomFieldDefinitions,
) {
    post<ClientImportParseRequest, ClientImportParseResponse>("/clients/import/parse") { request ->
        importClientsParse(request).bind()
    }

    post<ClientImportCommitRequest, ClientImportCommitResponse>("/clients/import/commit") { request ->
        importClientsCommit(request, clients, balances, leadSources, definitions).bind()
    }
}
