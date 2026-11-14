package org.athletica.crm.read.groups

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.groups.GroupClientAttention
import org.athletica.crm.api.schemas.groups.GroupRosterClient
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DB-тесты проекции состава группы: выбор абонемента, причина внимания, порядок и скоуп. */
class GroupRosterViewTest {
    private val fixture = MembershipFixture()
    private val view = DbGroupRosterView()
    private val ctx: EmployeeRequestContext = employeeContext(orgId = fixture.orgId, branchId = fixture.branchId)

    /** Сегодня организации: дата часов фикстуры, которые передаются в проекцию. */
    private val today: LocalDate = LocalDate(2026, 10, 16)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun roster(
        groupId: GroupId = fixture.groupId,
        context: EmployeeRequestContext = ctx,
    ): List<GroupRosterClient> = fixture.tx(context) { context(context) { view.list(GroupRosterQuery(groupId, fixture.clock.now())) } }.clients

    private suspend fun single(): GroupRosterClient = roster().single()

    /** Клиент [name], записанный в группу [groupId]. */
    private suspend fun member(
        name: String = "Иван",
        groupId: GroupId = fixture.groupId,
    ): ClientId =
        orThrow {
            val id = ClientId.new()
            TestPostgres.db
                .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                .bind("id", id)
                .bind("orgId", fixture.orgId)
                .bind("name", name)
                .execute()
            TestPostgres.db
                .sql("INSERT INTO enrollments (group_id, client_id) VALUES (:g, :c)")
                .bind("g", groupId)
                .bind("c", id)
                .execute()
            return id
        }

    /** Активный абонемент группы клиента [clientId], выданный сегодня на месяц. */
    private suspend fun active(
        clientId: ClientId,
        sessions: Int? = 8,
        groupIds: List<GroupId> = listOf(fixture.groupId),
    ): MembershipId = fixture.issue(clientId, sessions = sessions, issuedOn = today, groupIds = groupIds).id

    /** Неактивированный абонемент группы клиента [clientId] с активацией первым посещением. */
    private suspend fun inactive(clientId: ClientId): MembershipId = fixture.issue(clientId, activation = Activation.OnFirstVisit(null), issuedOn = today, name = "Следующий").id

    /** Проведённое занятие группы со вчерашней датой, на котором клиент [clientId] «пришёл» без списания. */
    private suspend fun unpaidVisit(clientId: ClientId) {
        orThrow {
            val hallId = HallId.new()
            val sessionId = SessionId.new()
            TestPostgres.db
                .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", hallId)
                .bind("name", "Зал $hallId")
                .bind("orgId", fixture.orgId)
                .bind("branchId", fixture.branchId)
                .execute()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, hall_id, status)
                    VALUES (:id, :orgId, :groupId, CURRENT_DATE - 1, '10:00'::time, '11:00'::time, :hallId,
                            'completed'::session_status)
                    """.trimIndent(),
                )
                .bind("id", sessionId)
                .bind("orgId", fixture.orgId)
                .bind("groupId", fixture.groupId)
                .bind("hallId", hallId)
                .execute()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO session_attendance (session_id, client_id, kind, presence)
                    VALUES (:s, :c, 'regular', 'present'::attendance_presence)
                    """.trimIndent(),
                )
                .bind("s", sessionId)
                .bind("c", clientId)
                .execute()
        }
    }

    /** Баланс клиента [clientId] равен [rubles] рублям. */
    private suspend fun balance(
        clientId: ClientId,
        rubles: Int,
    ) {
        orThrow {
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO client_balance_journal (org_id, client_id, amount, balance_after, operation_type, note, performed_by)
                    VALUES (:orgId, :c, :v, :v, 'admin_debit', 'тест', :e)
                    """.trimIndent(),
                )
                .bind("orgId", fixture.orgId)
                .bind("c", clientId)
                .bind("v", rubles.toBigDecimal())
                .bind("e", fixture.employeeId)
                .execute()
        }
    }

    @Test
    fun `группа другого филиала — GROUP_NOT_FOUND`() =
        runTest {
            member()
            val other = employeeContext(orgId = fixture.orgId)

            val error = fixture.attempt(other) { context(other) { view.list(GroupRosterQuery(fixture.groupId, fixture.clock.now())) } }.leftOrNull()

            assertEquals("GROUP_NOT_FOUND", assertIs<CommonDomainError>(error).code)
        }

    @Test
    fun `пустая группа — пустой состав`() =
        runTest {
            assertEquals(emptyList(), roster())
        }

    @Test
    fun `исключённого из группы нет в составе`() =
        runTest {
            val left = member("Ушедший")
            member("Оставшийся")
            orThrow {
                TestPostgres.db
                    .sql("UPDATE enrollments SET left_at = now() WHERE client_id = :c")
                    .bind("c", left)
                    .execute()
            }

            assertEquals(listOf("Оставшийся"), roster().map { it.name })
        }

    @Test
    fun `абонемент другой группы не действует в этой`() =
        runTest {
            val clientId = member()
            val other = GroupId.new()
            orThrow {
                TestPostgres.db
                    .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Взрослые')")
                    .bind("id", other)
                    .bind("orgId", fixture.orgId)
                    .bind("branchId", fixture.branchId)
                    .execute()
            }
            active(clientId, groupIds = listOf(other))

            val client = single()

            assertNull(client.membership)
            assertEquals(GroupClientAttention.NO_MEMBERSHIP, client.attention)
        }

    @Test
    fun `из активного и неактивированного выбран активный, следующий отмечен`() =
        runTest {
            val clientId = member()
            val activeId = active(clientId)
            inactive(clientId)

            val client = single()

            assertEquals(activeId, client.membership?.id)
            assertTrue(client.membership?.activated == true)
            assertTrue(client.hasNextMembership)
            assertNull(client.attention)
        }

    @Test
    fun `активный к закрытию уступает купленному неактивированному`() =
        runTest {
            val clientId = member()
            fixture.setRemaining(active(clientId), 0)
            val nextId = inactive(clientId)

            val client = single()

            assertEquals(nextId, client.membership?.id)
            assertFalse(client.membership?.activated == true)
            assertEquals(emptyList(), client.membership?.endingSoon)
            assertFalse(client.hasNextMembership)
            assertNull(client.attention)
        }

    @Test
    fun `посещение без абонемента — DEBT важнее NO_MEMBERSHIP`() =
        runTest {
            val clientId = member()
            unpaidVisit(clientId)

            val client = single()

            assertEquals(1, client.unpaidSessions)
            assertEquals(GroupClientAttention.DEBT, client.attention)
        }

    @Test
    fun `отрицательный баланс — DEBT`() =
        runTest {
            val clientId = member()
            active(clientId)
            balance(clientId, -500)

            val client = single()

            assertEquals(0, client.unpaidSessions)
            assertTrue(client.balance.isNegative)
            assertEquals(GroupClientAttention.DEBT, client.attention)
        }

    @Test
    fun `остаток исчерпан — NO_MEMBERSHIP`() =
        runTest {
            val clientId = member()
            fixture.setRemaining(active(clientId), 0)

            val client = single()

            assertTrue(client.membership?.closePending == true)
            assertEquals(GroupClientAttention.NO_MEMBERSHIP, client.attention)
        }

    @Test
    fun `последнее занятие — MEMBERSHIP_ENDING`() =
        runTest {
            val clientId = member()
            fixture.setRemaining(active(clientId), 1)

            val client = single()

            assertTrue(client.membership?.endingSoon?.isNotEmpty() == true)
            assertEquals(GroupClientAttention.MEMBERSHIP_ENDING, client.attention)
        }

    @Test
    fun `последнее занятие при купленном следующем — без причины`() =
        runTest {
            val clientId = member()
            fixture.setRemaining(active(clientId), 1)
            inactive(clientId)

            val client = single()

            assertTrue(client.hasNextMembership)
            assertNull(client.attention)
        }

    @Test
    fun `только неактивированный абонемент — без причины`() =
        runTest {
            val clientId = member()
            inactive(clientId)

            val client = single()

            assertFalse(client.membership?.activated == true)
            assertNull(client.attention)
        }

    @Test
    fun `состав упорядочен по причине, затем по имени`() =
        runTest {
            active(member("Белова"))
            fixture.setRemaining(active(member("Абрамов")), 1)
            unpaidVisit(member("Яковлева"))

            assertEquals(listOf("Яковлева", "Абрамов", "Белова"), roster().map { it.name })
        }

    @Test
    fun `срок вышел — NO_MEMBERSHIP`() =
        runTest {
            val clientId = member()
            active(clientId)
            fixture.at(LocalDate(2026, 11, 16))

            val client = single()

            assertTrue(client.membership?.closePending == true)
            assertEquals(GroupClientAttention.NO_MEMBERSHIP, client.attention)
        }

    @Test
    fun `до конца срока 7 дней — MEMBERSHIP_ENDING по дате`() =
        runTest {
            val clientId = member()
            active(clientId)
            fixture.at(LocalDate(2026, 11, 8))

            val client = single()

            assertEquals(listOf(EndingSoonReason.BY_DATE), client.membership?.endingSoon)
            assertEquals(GroupClientAttention.MEMBERSHIP_ENDING, client.attention)
        }

    @Test
    fun `активный с вышедшим сроком уступает действующему активному`() =
        runTest {
            val clientId = member()
            fixture.issue(clientId, issuedOn = LocalDate(2026, 9, 1), name = "Старый")
            val currentId = active(clientId)

            val client = single()

            assertEquals(currentId, client.membership?.id)
            assertNull(client.attention)
        }

    @Test
    fun `долг на активном абонементе с остатком можно погасить`() =
        runTest {
            val clientId = member()
            active(clientId)
            unpaidVisit(clientId)

            assertTrue(single().canRepay)
        }

    @Test
    fun `долг без абонемента или с исчерпанным остатком погасить нельзя`() =
        runTest {
            val withoutMembership = member("Без абонемента")
            unpaidVisit(withoutMembership)
            val exhausted = member("Исчерпан")
            fixture.setRemaining(active(exhausted), 0)
            unpaidVisit(exhausted)

            assertEquals(listOf(false, false), roster().map { it.canRepay })
        }
}
