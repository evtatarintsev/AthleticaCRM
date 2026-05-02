package org.athletica.crm.api.client

import io.ktor.client.HttpClient

/**
 * Корневой клиент для взаимодействия с API сервера.
 * Принимает настроенный [http] — Ktor HTTP клиент с аутентификацией и сериализацией.
 * Предоставляет доступ к специализированным клиентам для разных модулей.
 */
class ApiClient(private val http: HttpClient) {
    val auth = AuthApiClient(http)
    val profile = ProfileApiClient(http)
    val branches = BranchesApiClient(http)
    val clients = ClientsApiClient(http)
    val groups = GroupsApiClient(http)
    val org = OrganizationApiClient(http)
    val employees = EmployeesApiClient(http)
    val disciplines = DisciplinesApiClient(http)
    val documents = DocumentsApiClient(http)
    val audit = AuditApiClient(http)
    val notifications = NotificationsApiClient(http)
    val leadSources = LeadSourcesApiClient(http)
    val customFields = CustomFieldsApiClient(http)
    val halls = HallsApiClient(http)
    val sessions = SessionsApiClient(http)
    val home = HomeApiClient(http)
}
