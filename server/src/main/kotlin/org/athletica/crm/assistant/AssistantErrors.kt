package org.athletica.crm.assistant

import org.athletica.crm.core.errors.CommonDomainError

/** Стабильный код для локализации ошибки помощника на клиенте, без данных переписки. */
internal fun assistantError(reason: String) = CommonDomainError("ASSISTANT_$reason", "AI assistant: $reason")
