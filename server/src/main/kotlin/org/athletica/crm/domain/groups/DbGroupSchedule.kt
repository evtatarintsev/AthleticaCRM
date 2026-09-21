package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSlotId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.time.Validity
import org.athletica.crm.domain.sessions.UntouchedSessions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [GroupSchedule] поверх PostgreSQL: версии слотов в `schedule_slots.validity`. */
class DbGroupSchedule : GroupSchedule {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun slotsOn(groupId: GroupId, date: LocalDate): List<ScheduleSlot> =
        tr
            .sql("$SELECT_SLOTS AND ss.validity @> :date::date ORDER BY ss.day_of_week, ss.start_time")
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .bind("date", date)
            .list { row -> row.toSlot() }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun slotsDuring(groupId: GroupId, from: LocalDate, to: LocalDate): List<ScheduleSlot> =
        tr
            .sql(
                "$SELECT_SLOTS AND ss.validity && daterange(:from::date, :to::date, '[]') " +
                    "ORDER BY lower(ss.validity), ss.day_of_week, ss.start_time",
            )
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .bind("from", from)
            .bind("to", to)
            .list { row -> row.toSlot() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun setFrom(groupId: GroupId, effectiveFrom: LocalDate?, slots: List<NewSlot>) {
        val today = java.time.LocalDate.now().toKotlinLocalDate()
        val from = effectiveFrom ?: today
        if (from < today) {
            raise(CommonDomainError("SCHEDULE_EFFECTIVE_FROM_IN_PAST", Messages.ScheduleEffectiveFromInPast.localize()))
        }
        slots.validated()
        requireGroupExists(groupId)
        slots.forEach { requireHallBelongsToGroup(groupId, it) }

        plannedAfter(groupId, from).forEach { dropVersion(it) }

        val kept = mutableSetOf<Pair<DayOfWeek, LocalTime>>()
        slotsOn(groupId, from).forEach { current ->
            val replacement = slots.firstOrNull { it.key == (current.dayOfWeek to current.startAt) }
            when {
                replacement != null && current.matches(replacement) -> {
                    kept += replacement.key
                    extendForever(current)
                }
                current.validity.from < from -> closeAt(current, from)
                else -> dropVersion(current.id)
            }
        }

        slots.filterNot { it.key in kept }.forEach { insert(groupId, it, from) }
    }

    /** Отклоняет повторяющиеся правила и правила с некорректным временем. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun List<NewSlot>.validated() {
        forEach { it.validate() }
        groupingBy { it.key }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.let { (key, _) ->
                raise(
                    CommonDomainError(
                        "DUPLICATE_SCHEDULE_SLOT",
                        Messages.DuplicateScheduleSlot.localize(key.first.name, key.second),
                    ),
                )
            }
    }

    /** Убеждается, что группа существует в организации контекста. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun requireGroupExists(groupId: GroupId) {
        tr
            .sql("SELECT 1 AS ok FROM groups WHERE id = :groupId AND org_id = :orgId")
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
    }

    /** Убеждается, что зал слота принадлежит филиалу группы. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun requireHallBelongsToGroup(groupId: GroupId, slot: NewSlot) {
        tr
            .sql(
                """
                SELECT 1 AS ok
                FROM halls h
                JOIN groups g ON g.branch_id = h.branch_id AND g.org_id = h.org_id
                WHERE h.id = :hallId AND g.id = :groupId AND g.org_id = :orgId
                """.trimIndent(),
            )
            .bind("hallId", slot.hallId)
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))
    }

    /** Версии, которые начинают действовать строго позже [from] — отложенные изменения. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun plannedAfter(groupId: GroupId, from: LocalDate): List<SlotId> =
        tr
            .sql(
                """
                SELECT ss.id
                FROM schedule_slots ss
                WHERE ss.group_id = :groupId AND ss.org_id = :orgId AND lower(ss.validity) > :from
                """.trimIndent(),
            )
            .bind("groupId", groupId)
            .bind("orgId", ctx.orgId)
            .bind("from", from)
            .list { row -> row.asUuid("id").toSlotId() }

    /** Снимает ограничение периода сверху: версия действует бессрочно. */
    context(tr: Transaction)
    private suspend fun extendForever(slot: ScheduleSlot) {
        if (slot.validity.to == null) {
            return
        }
        tr
            .sql("UPDATE schedule_slots SET validity = daterange(lower(validity), NULL) WHERE id = :id")
            .bind("id", slot.id)
            .execute()
    }

    /** Закрывает версию датой [date]: она перестаёт действовать с этого дня. */
    context(tr: Transaction)
    private suspend fun closeAt(slot: ScheduleSlot, date: LocalDate) {
        tr
            .sql("UPDATE schedule_slots SET validity = daterange(lower(validity), :date::date) WHERE id = :id")
            .bind("id", slot.id)
            .bind("date", date)
            .execute()
    }

    /**
     * Удаляет версию слота, предварительно открепив от неё занятия:
     * нетронутые удаляются вместе с правилом, изменённые человеком теряют происхождение и остаются.
     * Без открепления `ON DELETE RESTRICT` уронил бы транзакцию.
     */
    context(tr: Transaction)
    private suspend fun dropVersion(slotId: SlotId) {
        tr
            .sql("DELETE FROM sessions s WHERE s.origin_slot_id = :slotId AND ${UntouchedSessions.predicate("s")}")
            .bind("slotId", slotId)
            .execute()
        tr
            .sql("UPDATE sessions SET origin_slot_id = NULL, origin_date = NULL WHERE origin_slot_id = :slotId")
            .bind("slotId", slotId)
            .execute()
        tr
            .sql("DELETE FROM schedule_slots WHERE id = :slotId")
            .bind("slotId", slotId)
            .execute()
    }

    /** Создаёт новую бессрочную версию правила [slot], действующую с [from]. */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun insert(groupId: GroupId, slot: NewSlot, from: LocalDate) {
        tr
            .sql(
                """
                INSERT INTO schedule_slots (id, org_id, group_id, day_of_week, start_time, end_time, hall_id, validity)
                VALUES (:id, :orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time, :hallId,
                        daterange(:from::date, NULL::date))
                """.trimIndent(),
            )
            .bind("id", SlotId.new())
            .bind("orgId", ctx.orgId)
            .bind("groupId", groupId)
            .bind("dayOfWeek", slot.dayOfWeek.name)
            .bind("startAt", slot.startAt.toString())
            .bind("endAt", slot.endAt.toString())
            .bind("hallId", slot.hallId)
            .bind("from", from)
            .execute()
    }

    private companion object {
        val SELECT_SLOTS =
            """
            SELECT ss.id, ss.group_id, ss.day_of_week, ss.start_time, ss.end_time, ss.hall_id,
                   lower(ss.validity) AS valid_from, upper(ss.validity) AS valid_to
            FROM schedule_slots ss
            WHERE ss.group_id = :groupId AND ss.org_id = :orgId
            """.trimIndent()
    }
}

/**
 * Собирает [ScheduleSlot] из строки выборки.
 * Период в БД защищён `CHECK`-ограничениями, поэтому [Validity.of] здесь не может вернуть ошибку.
 */
private fun io.r2dbc.spi.Row.toSlot(): ScheduleSlot {
    val from = asLocalDateOrNull("valid_from")!!
    val to = asLocalDateOrNull("valid_to")
    return ScheduleSlot(
        id = asUuid("id").toSlotId(),
        groupId = asUuid("group_id").toGroupId(),
        dayOfWeek = DayOfWeek.valueOf(asString("day_of_week")),
        startAt = asLocalTime("start_time"),
        endAt = asLocalTime("end_time"),
        hallId = asUuid("hall_id").toHallId(),
        validity = Validity.of(from, to).getOrNull()!!,
    )
}
