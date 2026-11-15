package org.athletica.crm.assistant

import org.athletica.crm.api.schemas.assistant.AssistantConfirmRequest
import org.athletica.crm.api.schemas.assistant.AssistantConfirmResponse
import org.athletica.crm.api.schemas.assistant.AssistantDialogResponse
import org.athletica.crm.api.schemas.assistant.AssistantSendRequest
import org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest
import org.athletica.crm.api.schemas.assistant.AssistantSettingsResponse
import org.athletica.crm.api.schemas.assistant.AssistantStatusResponse
import org.athletica.crm.routes.RouteWithContext

/** Аутентифицированный API помощника; вся прикладная логика остаётся в его модуле. */
fun RouteWithContext.assistantRoutes(assistant: AssistantService) {
    get<Unit, AssistantStatusResponse>("/assistant/status") { assistant.status() }
    get<Unit, AssistantSettingsResponse>("/assistant/settings") { assistant.settings() }
    post<AssistantSettingsRequest, AssistantSettingsResponse>("/assistant/settings/update") { assistant.updateSettings(it) }
    get<Unit, AssistantDialogResponse>("/assistant/dialogs/current") { assistant.current() }
    post<Unit, AssistantDialogResponse>("/assistant/dialogs/create") { assistant.create() }
    post<AssistantSendRequest, AssistantDialogResponse>("/assistant/messages/send") { assistant.send(it.dialogId, it.text) }
    post<AssistantConfirmRequest, AssistantConfirmResponse>("/assistant/drafts/confirm") { AssistantConfirmResponse(assistant.confirm(it.clientId).clientId) }
}
