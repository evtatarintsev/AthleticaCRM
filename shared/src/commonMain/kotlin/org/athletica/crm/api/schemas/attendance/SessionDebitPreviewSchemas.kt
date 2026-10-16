package org.athletica.crm.api.schemas.attendance

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId

/** Запрос превью списаний абонементов для участников занятия. */
@Serializable
data class SessionDebitPreviewRequest(
    /** Занятие. */
    val sessionId: SessionId,
)

/** Превью списаний для всех участников занятия. */
@Serializable
data class SessionDebitPreviewResponse(
    /** Участники занятия с их списанием. */
    val participants: List<ParticipantDebitSchema>,
)

/** Списание участника [clientId]. */
@Serializable
data class ParticipantDebitSchema(
    /** Клиент-участник. */
    val clientId: ClientId,
    /** С какого абонемента списано, будет списано или что абонемента нет. */
    val debit: ParticipantDebitStateSchema,
)

/** Абонемент списания: название и остаток. */
@Serializable
data class DebitMembershipSchema(
    /** Абонемент. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
)

/** Списание участника (дискриминатор `type`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ParticipantDebitStateSchema

/** Занятие проведено, занятие списано с [membership]. */
@Serializable
@SerialName("debited")
data class DebitedParticipantDebitSchema(
    /** Абонемент, с которого списано. */
    val membership: DebitMembershipSchema,
) : ParticipantDebitStateSchema

/** Занятие ещё не проведено, при проведении спишется с [membership]. */
@Serializable
@SerialName("will_debit")
data class WillDebitParticipantDebitSchema(
    /** Абонемент, с которого спишется занятие. */
    val membership: DebitMembershipSchema,
    /** Абонемент не активирован и активируется этим занятием. */
    val activates: Boolean,
) : ParticipantDebitStateSchema

/** Подходящего абонемента нет: при проведении занятие станет неоплаченным (или уже стало). */
@Serializable
@SerialName("no_membership")
data object NoMembershipParticipantDebitSchema : ParticipantDebitStateSchema

/** Занятие проведено, участник не пришёл — списания нет и не нужно. */
@Serializable
@SerialName("not_debited")
data object NotDebitedParticipantDebitSchema : ParticipantDebitStateSchema
