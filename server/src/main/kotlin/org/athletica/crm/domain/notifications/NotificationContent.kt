package org.athletica.crm.domain.notifications

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.i18n.Messages

/**
 * Содержимое уведомления: что произошло и с какими параметрами.
 * Каждая разновидность сама собирает заголовок и текст на нужном языке
 * и знает, к какому объекту относится.
 *
 * Сериализуется в колонку `notifications.content`; дискриминатор `kind` — вид уведомления.
 * Параметры — снимок на момент события: переименование или удаление объекта
 * не делает уже отправленное уведомление бессмысленным.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface NotificationContent {
    /** Объект уведомления; [NotificationSubject.None], если ссылаться не на что. */
    val subject: NotificationSubject

    /** Заголовок на языке [lang]. */
    fun title(lang: Lang): String

    /** Текст на языке [lang]. */
    fun body(lang: Lang): String
}

/** Изменено расписание группы. */
@Serializable
@SerialName("group_schedule_changed")
data class GroupScheduleChanged(
    /** Группа, расписание которой изменено. */
    val groupId: GroupId,
    /** Название группы на момент изменения. */
    val groupName: String,
    /** Дата, с которой действует новое расписание. */
    val effectiveFrom: LocalDate,
    /** Имя сотрудника, изменившего расписание. */
    val changedByName: String,
) : NotificationContent {
    override val subject: NotificationSubject
        get() = NotificationSubject.Group(groupId)

    override fun title(lang: Lang): String = Messages.GroupScheduleChangedTitle.localize(lang, groupName)

    override fun body(lang: Lang): String = Messages.GroupScheduleChangedBody.localize(lang, effectiveFrom, changedByName)
}

/** Абонемент клиента «к закрытию», а режим закрытия не закроет его сам. */
@Serializable
@SerialName("membership_close_pending")
data class MembershipClosePending(
    /** Клиент-владелец абонемента. */
    val clientId: ClientId,
    /** Абонемент «к закрытию». */
    val membershipId: MembershipId,
    /** Название абонемента на момент уведомления. */
    val membershipName: String,
) : NotificationContent {
    override val subject: NotificationSubject
        get() = NotificationSubject.Client(clientId)

    override fun title(lang: Lang): String = Messages.MembershipClosePendingTitle.localize(lang, membershipName)

    override fun body(lang: Lang): String = Messages.MembershipClosePendingBody.localize(lang)
}
