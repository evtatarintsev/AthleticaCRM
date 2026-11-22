package org.athletica.crm.read.clients

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.ActiveClientListMembershipState
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.InactiveClientListMembershipState
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.core.subscription.TermPosition
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DB-тесты проекции списка клиентов: абонементы клиента с признаками на сегодня и неоплаченные занятия. */
class ClientListMembershipsTest {
    private val fixture = MembershipFixture(membershipPrice = Money.zero(Currency.RUB))
    private val view = DbClientListView()
    private val ctx: EmployeeRequestContext = employeeContext(orgId = fixture.orgId, branchId = fixture.branchId)

    /** Сегодня организации в сценариях. */
    private val today = LocalDate(2026, 11, 20)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    /** Страница списка на полдень [today] с размером [limit] и смещением [offset]. */
    private suspend fun page(
        limit: Int? = 50,
        offset: Int = 0,
    ): ClientListResponse =
        fixture.tx(ctx) {
            context(ctx) {
                view.page(ClientListQuery(now = today.atTime(LocalTime(12, 0)).toInstant(TimeZone.UTC), limit = limit, offset = offset))
            }
        }

    /** Единственный клиент страницы. */
    private suspend fun single(): ClientListItem = page().clients.single()

    /** Клиент организации с именем [name]. */
    private suspend fun client(name: String = "Анна"): ClientId =
        orThrow {
            val id = ClientId.new()
            TestPostgres.db
                .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'FEMALE'::gender)")
                .bind("id", id)
                .bind("orgId", fixture.orgId)
                .bind("name", name)
                .execute()
            return id
        }

    /** Активный абонемент [name] клиента [clientId] со сроком [start]..[end] и остатком [remaining] из [total]. */
    private suspend fun active(
        clientId: ClientId,
        name: String,
        start: LocalDate,
        end: LocalDate,
        total: Int? = 8,
        remaining: Int? = total,
    ): MembershipId {
        val id = fixture.issue(clientId, sessions = total, issuedOn = LocalDate(2026, 11, 1), name = name).id
        orThrow {
            TestPostgres.db
                .sql("UPDATE memberships SET start_date = :start, end_date = :end, sessions_remaining = :remaining WHERE id = :id")
                .bind("start", start)
                .bind("end", end)
                .bind("remaining", remaining)
                .bind("id", id)
                .execute()
        }
        return id
    }

    /** Неактивированный абонемент [name] клиента [clientId]. */
    private suspend fun inactive(
        clientId: ClientId,
        name: String,
        activation: Activation = Activation.OnFirstVisit(null),
    ): MembershipId = fixture.issue(clientId, activation = activation, issuedOn = today, name = name).id

    /** Переводит абонемент [id] в сохранённое состояние [state]. */
    private suspend fun setState(
        id: MembershipId,
        state: String,
    ) {
        orThrow {
            TestPostgres.db
                .sql(
                    """
                    UPDATE memberships
                       SET state = :state,
                           closed_on = CASE WHEN :state = 'CLOSED' THEN end_date END,
                           close_reason = CASE WHEN :state = 'CLOSED' THEN 'MANUAL' END
                     WHERE id = :id
                    """.trimIndent(),
                )
                .bind("state", state)
                .bind("id", id)
                .execute()
        }
    }

    /** Группа [name] филиала [branchId]. */
    private suspend fun group(
        name: String,
        branchId: BranchId = fixture.branchId,
    ): GroupId =
        orThrow {
            val id = GroupId.new()
            TestPostgres.db
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", id)
                .bind("orgId", fixture.orgId)
                .bind("branchId", branchId)
                .bind("name", name)
                .execute()
            return id
        }

    /** Ещё один филиал организации. */
    private suspend fun otherBranch(): BranchId =
        orThrow {
            val id = BranchId.new()
            TestPostgres.db
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Север')")
                .bind("id", id)
                .bind("orgId", fixture.orgId)
                .execute()
            return id
        }

    /** Записывает клиента [clientId] в группу [groupId]. */
    private suspend fun enroll(
        clientId: ClientId,
        groupId: GroupId,
    ) {
        orThrow {
            TestPostgres.db
                .sql("INSERT INTO enrollments (group_id, client_id) VALUES (:g, :c)")
                .bind("g", groupId)
                .bind("c", clientId)
                .execute()
        }
    }

    /**
     * Занятие группы [groupId] филиала [branchId] в состоянии [status], на котором клиент [clientId]
     * отмечен с присутствием [presence]; возвращает id занятия.
     */
    private suspend fun visit(
        clientId: ClientId,
        groupId: GroupId = fixture.groupId,
        branchId: BranchId = fixture.branchId,
        status: String = "completed",
        presence: String = "present",
    ): SessionId =
        orThrow {
            val hallId = HallId.new()
            val sessionId = SessionId.new()
            TestPostgres.db
                .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", hallId)
                .bind("name", "Зал $hallId")
                .bind("orgId", fixture.orgId)
                .bind("branchId", branchId)
                .execute()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, hall_id, status)
                    VALUES (:id, :orgId, :groupId, :date, '10:00'::time, '11:00'::time, :hallId, :status::session_status)
                    """.trimIndent(),
                )
                .bind("id", sessionId)
                .bind("orgId", fixture.orgId)
                .bind("groupId", groupId)
                .bind("date", LocalDate(2026, 11, 19))
                .bind("hallId", hallId)
                .bind("status", status)
                .execute()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO session_attendance (session_id, client_id, kind, presence)
                    VALUES (:s, :c, 'regular', :presence::attendance_presence)
                    """.trimIndent(),
                )
                .bind("s", sessionId)
                .bind("c", clientId)
                .bind("presence", presence)
                .execute()
            return sessionId
        }

    /** Списание абонемента [membershipId] за занятие [sessionId]; [reversed] — сторнировано. */
    private suspend fun debit(
        clientId: ClientId,
        membershipId: MembershipId,
        sessionId: SessionId,
        reversed: Boolean,
    ) {
        orThrow {
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO membership_debits (org_id, membership_id, client_id, session_id, kind, reversed_at)
                    VALUES (:orgId, :m, :c, :s, 'REGULAR', CASE WHEN :reversed THEN now() END)
                    """.trimIndent(),
                )
                .bind("orgId", fixture.orgId)
                .bind("m", membershipId)
                .bind("c", clientId)
                .bind("s", sessionId)
                .bind("reversed", reversed)
                .execute()
        }
    }

    @Test
    fun `два активных абонемента идут по дате окончания`() =
        runTest {
            val anna = client()
            active(anna, "Юниор", LocalDate(2026, 11, 1), LocalDate(2026, 12, 31), total = null)
            active(anna, "Мини 8", LocalDate(2026, 11, 3), LocalDate(2026, 12, 3), total = 12, remaining = 8)

            val memberships = single().memberships
            assertEquals(listOf("Мини 8", "Юниор"), memberships.map { it.name })
            val mini = memberships.first()
            assertEquals(12, mini.sessionsTotal)
            assertEquals(8, mini.sessionsRemaining)
            val state = assertIs<ActiveClientListMembershipState>(mini.state)
            assertEquals(TermPosition.IN_TERM, state.termPosition)
            assertEquals(LocalDate(2026, 12, 3), state.endDate)
            assertTrue(state.endingSoon.isEmpty())
            assertEquals(false, state.closePending)
            assertEquals(null, memberships.last().sessionsTotal)
        }

    @Test
    fun `абонемент клиента без группы отдаётся`() =
        runTest {
            client()
            val item = single()
            assertTrue(item.memberships.isEmpty())

            inactive(item.id, "Мини 8", Activation.OnDate(LocalDate(2026, 12, 1)))
            val membership = single().memberships.single()
            assertEquals("Мини 8", membership.name)
            val state = assertIs<InactiveClientListMembershipState>(membership.state)
            assertEquals(ActivationMode.ON_DATE, state.activationMode)
            assertEquals(LocalDate(2026, 12, 1), state.activateOn)
        }

    @Test
    fun `закрытый и отозванный абонементы не отдаются`() =
        runTest {
            val anna = client()
            setState(active(anna, "Старый", LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), "CLOSED")
            setState(active(anna, "Отозванный", LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)), "REVOKED")

            assertTrue(single().memberships.isEmpty())
        }

    @Test
    fun `действующий раньше неактивированного и к закрытию`() =
        runTest {
            val anna = client()
            inactive(anna, "Следующий")
            active(anna, "Истёкший", LocalDate(2026, 10, 15), LocalDate(2026, 11, 15), remaining = 2)
            active(anna, "Текущий", LocalDate(2026, 11, 1), LocalDate(2026, 12, 30))

            val memberships = single().memberships
            assertEquals(listOf("Текущий", "Истёкший", "Следующий"), memberships.map { it.name })
            val expired = assertIs<ActiveClientListMembershipState>(memberships[1].state)
            assertEquals(TermPosition.EXPIRED, expired.termPosition)
            assertTrue(expired.closePending)
            val next = assertIs<InactiveClientListMembershipState>(memberships[2].state)
            assertEquals(ActivationMode.ON_FIRST_VISIT, next.activationMode)
            assertEquals(null, next.activateOn)
        }

    @Test
    fun `заканчивается по занятиям и по дате`() =
        runTest {
            val anna = client()
            active(anna, "Мини 8", LocalDate(2026, 11, 1), LocalDate(2026, 12, 20), remaining = 1)
            active(anna, "Юниор 8", LocalDate(2026, 10, 23), LocalDate(2026, 11, 22), remaining = 1)

            val (byBoth, bySessions) = single().memberships.map { assertIs<ActiveClientListMembershipState>(it.state) }
            assertEquals(listOf(EndingSoonReason.BY_DATE, EndingSoonReason.BY_SESSIONS), byBoth.endingSoon)
            assertEquals(listOf(EndingSoonReason.BY_SESSIONS), bySessions.endingSoon)
        }

    @Test
    fun `срок ещё не начался`() =
        runTest {
            val anna = client()
            active(anna, "Мини 8", LocalDate(2026, 12, 1), LocalDate(2026, 12, 31))

            val state = assertIs<ActiveClientListMembershipState>(single().memberships.single().state)
            assertEquals(TermPosition.NOT_STARTED, state.termPosition)
            assertTrue(state.endingSoon.isEmpty())
            assertEquals(false, state.closePending)
        }

    @Test
    fun `сегодня считается в часовом поясе организации`() =
        runTest {
            orThrow {
                TestPostgres.db
                    .sql("UPDATE organizations SET timezone = 'Asia/Vladivostok' WHERE id = :id")
                    .bind("id", fixture.orgId)
                    .execute()
            }
            val anna = client()
            active(anna, "Мини 8", LocalDate(2026, 10, 21), LocalDate(2026, 11, 20))
            val lateEvening = LocalDate(2026, 11, 20).atTime(LocalTime(20, 0)).toInstant(TimeZone.UTC)

            val state =
                fixture.tx(ctx) {
                    context(ctx) { view.page(ClientListQuery(now = lateEvening)) }
                }.clients.single().memberships.single().state
            assertEquals(TermPosition.EXPIRED, assertIs<ActiveClientListMembershipState>(state).termPosition)
        }

    @Test
    fun `неоплаченные в разных группах филиала`() =
        runTest {
            val anna = client()
            val mini = group("Мини")
            enroll(anna, mini)
            visit(anna, mini)
            visit(anna, group("Юниор"))

            assertEquals(2, single().unpaidSessions)
        }

    @Test
    fun `неоплаченные другого филиала не считаются`() =
        runTest {
            val anna = client()
            visit(anna)
            val north = otherBranch()
            visit(anna, group("Север", north), north)

            assertEquals(1, single().unpaidSessions)
        }

    @Test
    fun `сторнированное списание — занятие неоплачено, действующее — оплачено`() =
        runTest {
            val anna = client()
            val membership = active(anna, "Мини 8", LocalDate(2026, 11, 1), LocalDate(2026, 12, 1))
            debit(anna, membership, visit(anna), reversed = true)
            debit(anna, membership, visit(anna), reversed = false)

            assertEquals(1, single().unpaidSessions)
        }

    @Test
    fun `не пришёл и непроведённое занятие не считаются, без долга — 0`() =
        runTest {
            val anna = client()
            assertEquals(0, single().unpaidSessions)

            visit(anna, presence = "absent")
            visit(anna, status = "scheduled")
            assertEquals(0, single().unpaidSessions)
        }

    @Test
    fun `пагинация не меняется`() =
        runTest {
            val clients = (1..120).map { client("Клиент %03d".format(it)) }
            listOf(clients.first(), clients.last()).forEach {
                active(it, "Мини 8", LocalDate(2026, 11, 1), LocalDate(2026, 12, 1))
                visit(it)
            }

            val first = page(limit = 50, offset = 0)
            val last = page(limit = 50, offset = 100)
            assertEquals(120u, first.total)
            assertEquals(120u, last.total)
            assertEquals(50, first.clients.size)
            assertEquals(20, last.clients.size)
            listOf(first.clients.first(), last.clients.last()).forEach {
                assertEquals(listOf("Мини 8"), it.memberships.map { m -> m.name })
                assertEquals(1, it.unpaidSessions)
            }
            assertTrue(first.clients.drop(1).all { it.memberships.isEmpty() && it.unpaidSessions == 0 })
        }
}
