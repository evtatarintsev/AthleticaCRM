package org.athletica.crm.domain.events

import kotlinx.serialization.Serializable

/**
 * Базовый тип доменного события.
 * Все подтипы сериализуются в JSON и хранятся в таблице [domain_events] (outbox-паттерн).
 * [OrgId] хранится отдельно в колонке `org_id` и не дублируется в payload события.
 */
@Serializable
sealed interface DomainEvent
