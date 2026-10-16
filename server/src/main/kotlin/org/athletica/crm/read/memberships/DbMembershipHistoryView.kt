package org.athletica.crm.read.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.api.schemas.memberships.MembershipActivatedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipCloseNotifiedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipClosedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipHistoryEntrySchema
import org.athletica.crm.api.schemas.memberships.MembershipHistoryResponse
import org.athletica.crm.api.schemas.memberships.MembershipIssuedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipRevokedEventSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Реализация [MembershipHistoryView] поверх `membership_events` одним запросом. */
class DbMembershipHistoryView : MembershipHistoryView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byMembership(membershipId: MembershipId): MembershipHistoryResponse {
        tr
            .sql("SELECT 1 FROM memberships WHERE id = :id AND org_id = :orgId")
            .bind("id", membershipId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("MEMBERSHIP_NOT_FOUND", Messages.MembershipNotFound.localize()))
        val entries =
            tr
                .sql(
                    """
                    SELECT e.id, e.occurred_at, e.actor_id, emp.name AS actor_name, e.kind, e.payload::text AS payload
                    FROM membership_events e
                    LEFT JOIN employees emp ON emp.id = e.actor_id
                    WHERE e.membership_id = :id AND e.org_id = :orgId
                    ORDER BY e.occurred_at DESC, e.id DESC
                    """.trimIndent(),
                )
                .bind("id", membershipId)
                .bind("orgId", ctx.orgId)
                .list { it.toEntry() }
        return MembershipHistoryResponse(entries)
    }

    /** Собирает запись истории из строки результата. */
    private fun Row.toEntry(): MembershipHistoryEntrySchema =
        MembershipHistoryEntrySchema(
            id = asUuid("id"),
            occurredAt = asInstant("occurred_at"),
            actorId = asUuidOrNull("actor_id")?.toEmployeeId(),
            actorName = asStringOrNull("actor_name"),
            event = eventOf(asString("kind"), Json.parseToJsonElement(asString("payload")).jsonObject),
        )

    /** Действие истории вида [kind] с данными [payload]. */
    private fun eventOf(kind: String, payload: JsonObject): MembershipEventSchema =
        when (kind) {
            "ISSUED" ->
                MembershipIssuedEventSchema(
                    activationMode = ActivationMode.valueOf(payload.string("activationMode")),
                    activateOn = payload.dateOrNull("activateOn"),
                    activationDeadline = payload.dateOrNull("activationDeadline"),
                    closeMode = CloseMode.valueOf(payload.string("closeMode")),
                )
            "ACTIVATED" ->
                MembershipActivatedEventSchema(
                    reason = ActivationReason.valueOf(payload.string("reason")),
                    startDate = LocalDate.parse(payload.string("startDate")),
                    endDate = LocalDate.parse(payload.string("endDate")),
                )
            "CLOSE_NOTIFIED" -> MembershipCloseNotifiedEventSchema
            "CLOSED" ->
                MembershipClosedEventSchema(
                    reason = CloseReason.valueOf(payload.string("reason")),
                    closedOn = LocalDate.parse(payload.string("closedOn")),
                )
            "REVOKED" -> MembershipRevokedEventSchema
            else -> error("Неизвестный вид записи истории абонемента: $kind")
        }

    /** Строковое значение обязательного поля [key] данных записи. */
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** Дата из необязательного поля [key] данных записи; `null`, если поля нет. */
    private fun JsonObject.dateOrNull(key: String): LocalDate? = get(key)?.jsonPrimitive?.content?.let { LocalDate.parse(it) }
}
