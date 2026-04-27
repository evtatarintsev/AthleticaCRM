package org.athletica.crm.domain.events

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/**
 * Базовый тип доменного события.
 * Все подтипы сериализуются в JSON и хранятся в таблице [domain_events] (outbox-паттерн).
 * [OrgId] хранится отдельно в колонке `org_id` и не дублируется в payload события.
 */
@Serializable
sealed interface DomainEvent

/** Группа создана — нужно сгенерировать занятия по расписанию на горизонт 8 недель. */
@Serializable
data class GroupCreated(
    /** Идентификатор созданной группы. */
    val groupId: GroupId,
) : DomainEvent

/** Расписание группы изменено — нужно сгенерировать занятия по новым слотам. */
@Serializable
data class GroupScheduleChanged(
    /** Идентификатор группы, расписание которой изменилось. */
    val groupId: GroupId,
) : DomainEvent
