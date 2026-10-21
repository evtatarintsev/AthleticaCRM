package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.UnfreezeReason
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Запрос истории абонемента. */
@Serializable
data class MembershipHistoryRequest(
    /** Абонемент, чья история запрашивается. */
    val membershipId: MembershipId,
)

/** История абонемента от новых записей к старым. */
@Serializable
data class MembershipHistoryResponse(
    /** Записи истории, новые сверху. */
    val entries: List<MembershipHistoryEntrySchema>,
)

/** Запись истории абонемента. */
@Serializable
data class MembershipHistoryEntrySchema(
    /** Идентификатор записи. */
    val id: Uuid,
    /** Момент действия. */
    val occurredAt: Instant,
    /** Сотрудник-автор; `null` — действие системы. */
    val actorId: EmployeeId?,
    /** Имя сотрудника-автора; `null` — действие системы. */
    val actorName: String?,
    /** Действие и его данные. */
    val event: MembershipEventSchema,
)

/** Действие в истории абонемента (дискриминатор `type`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MembershipEventSchema

/** Выдача абонемента с режимами активации и закрытия. */
@Serializable
@SerialName("issued")
data class MembershipIssuedEventSchema(
    /** Режим активации. */
    val activationMode: ActivationMode,
    /** Дата активации для режима «с даты». */
    val activateOn: LocalDate?,
    /** Крайняя дата активации для режима «при первом посещении». */
    val activationDeadline: LocalDate?,
    /** Режим закрытия. */
    val closeMode: CloseMode,
) : MembershipEventSchema

/** Активация абонемента. */
@Serializable
@SerialName("activated")
data class MembershipActivatedEventSchema(
    /** Причина активации. */
    val reason: ActivationReason,
    /** Дата начала действия. */
    val startDate: LocalDate,
    /** Дата окончания действия. */
    val endDate: LocalDate,
) : MembershipEventSchema

/** Отправка выдавшему уведомления «к закрытию». */
@Serializable
@SerialName("close_notified")
data object MembershipCloseNotifiedEventSchema : MembershipEventSchema

/** Закрытие абонемента. */
@Serializable
@SerialName("closed")
data class MembershipClosedEventSchema(
    /** Причина закрытия. */
    val reason: CloseReason,
    /** Дата закрытия. */
    val closedOn: LocalDate,
) : MembershipEventSchema

/** Отзыв абонемента. */
@Serializable
@SerialName("revoked")
data object MembershipRevokedEventSchema : MembershipEventSchema

/** Списание занятия с абонемента. */
@Serializable
@SerialName("debited")
data class MembershipDebitedEventSchema(
    /** Занятие. */
    val sessionId: SessionId,
    /** Дата занятия. */
    val sessionDate: LocalDate,
    /** Списание — погашение долга сотрудником, а не списание при проведении. */
    val debtRepayment: Boolean,
) : MembershipEventSchema

/** Возврат занятия на абонемент при правке отметки. */
@Serializable
@SerialName("debit_reversed")
data class MembershipDebitReversedEventSchema(
    /** Занятие. */
    val sessionId: SessionId,
    /** Дата занятия. */
    val sessionDate: LocalDate,
) : MembershipEventSchema

/** Заморозка абонемента. */
@Serializable
@SerialName("frozen")
data class MembershipFrozenEventSchema(
    /** Первый день заморозки. */
    val startDate: LocalDate,
    /** Последний день заморозки включительно. */
    val endDate: LocalDate,
    /** Причина заморозки. */
    val reason: String,
    /** Дата окончания абонемента после сдвига на длину заморозки. */
    val membershipEndDate: LocalDate,
) : MembershipEventSchema

/** Снятие заморозки абонемента. */
@Serializable
@SerialName("unfrozen")
data class MembershipUnfrozenEventSchema(
    /** Основание снятия. */
    val reason: UnfreezeReason,
    /** Первый день снятой заморозки. */
    val startDate: LocalDate,
    /** Последний день снятой заморозки. */
    val endDate: LocalDate,
    /** Дата окончания абонемента после возврата неиспользованных дней. */
    val membershipEndDate: LocalDate,
) : MembershipEventSchema
