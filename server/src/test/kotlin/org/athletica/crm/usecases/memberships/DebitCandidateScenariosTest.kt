package org.athletica.crm.usecases.memberships

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.attendance.NoMembershipParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.WillDebitParticipantDebitSchema
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.org.dateIn
import org.athletica.crm.read.sessions.DbSessionDebitPreviewView
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * Общий набор сценариев подбора абонемента (спека «Подбор абонемента для списания»).
 * Каждый сценарий прогоняется и на `DbMemberships.debitCandidate`, и на превью
 * `DbSessionDebitPreviewView` — страховка от расхождения обвязки вокруг `MembershipCandidateSql`.
 * Даты отсчитываются от настоящего «сегодня»: превью берёт его из `now()` базы.
 */
class DebitCandidateScenariosTest {
    private val fixture = MembershipFixture()
    private val debits = DebitFixture(fixture)
    private val preview = DbSessionDebitPreviewView()
    private val today = dateIn("UTC", Clock.System.now())

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
        }
        fixture.at(today)
    }

    private fun days(n: Int) = if (n >= 0) today.plus(DatePeriod(days = n)) else today.minus(DatePeriod(days = -n))

    /**
     * Сценарий: [setup] выдаёт абонементы клиенту и возвращает дату занятия;
     * [expected] — название подобранного абонемента или `null`.
     */
    class Scenario(
        /** Название сценария для сообщения об ошибке. */
        val name: String,
        /** Ожидаемый абонемент; `null` — подходящего нет. */
        val expected: String?,
        /** Подготовка: абонементы клиента [ClientId]; возвращает дату занятия. */
        val setup: suspend (ClientId) -> LocalDate,
    )

    private val scenarios =
        listOf(
            Scenario("приоритет более раннего окончания", "Ранний") { client ->
                val late = fixture.issue(client, name = "Поздний", issuedOn = today)
                val early = fixture.issue(client, name = "Ранний", issuedOn = today)
                debits.setTerm(late.id, days(-5), days(40))
                debits.setTerm(early.id, days(-5), days(9))
                today
            },
            Scenario("активный приоритетнее неактивированного", "Активный") { client ->
                fixture.issue(client, Activation.OnFirstVisit(null), name = "Первое посещение", issuedOn = days(-3))
                fixture.issue(client, name = "Активный", issuedOn = today)
                today
            },
            Scenario("дата занятия вне срока", null) { client ->
                val active = fixture.issue(client, name = "Активный", issuedOn = today)
                debits.setTerm(active.id, days(1), days(30))
                today
            },
            Scenario("исчерпанный не подбирается", null) { client ->
                val active = fixture.issue(client, name = "Исчерпан", issuedOn = today)
                fixture.setRemaining(active.id, 0)
                today
            },
            Scenario("первое посещение до даты выдачи", null) { client ->
                fixture.issue(client, Activation.OnFirstVisit(null), name = "Первое", issuedOn = today)
                days(-2)
            },
            Scenario("первое посещение после крайней даты", null) { client ->
                fixture.issue(client, Activation.OnFirstVisit(days(-1)), name = "Первое", issuedOn = days(-5))
                today
            },
            Scenario("первое посещение подбирается", "Первое") { client ->
                fixture.issue(client, Activation.OnFirstVisit(null), name = "Первое", issuedOn = days(-5))
                days(-2)
            },
            Scenario("закрытый по сроку при позднем проведении", null) { client ->
                val closed = fixture.issue(client, closeMode = CloseMode.ON_EXPIRY, name = "Закрыт", issuedOn = days(-40))
                TestPostgres.db
                    .sql("UPDATE memberships SET state = 'CLOSED', closed_on = :d, close_reason = 'EXPIRY' WHERE id = :id")
                    .bind("d", days(-1))
                    .bind("id", closed.id)
                    .execute()
                days(-10)
            },
            Scenario("с даты, наступившей сегодня, до фоновой активации", "С даты") { client ->
                fixture.issue(client, Activation.OnDate(days(1)), name = "С даты", issuedOn = days(-1))
                TestPostgres.db.sql("UPDATE memberships SET activate_on = :d").bind("d", today).execute()
                today
            },
            Scenario("будущая дата активации не подбирается", null) { client ->
                fixture.issue(client, Activation.OnDate(days(5)), name = "С даты", issuedOn = today)
                days(5)
            },
            Scenario("занятие чужой группы", null) { client ->
                val other = GroupId.new()
                TestPostgres.db
                    .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Взрослые')")
                    .bind("id", other)
                    .bind("orgId", fixture.orgId)
                    .bind("branchId", fixture.branchId)
                    .execute()
                fixture.issue(client, name = "Взрослые", issuedOn = today, groupIds = listOf(other))
                today
            },
        )

    @Test
    fun `сценарии подбора на списании`() =
        runTest {
            scenarios.forEach { scenario ->
                val client = fixture.insertClient()
                val sessionDate = scenario.setup(client)
                val picked =
                    fixture.tx(fixture.system) {
                        fixture.memberships.debitCandidate(client, fixture.groupId, sessionDate, today)?.name
                    }
                assertEquals(scenario.expected, picked, scenario.name)
                TestPostgres.db.sql("DELETE FROM memberships").execute()
            }
        }

    @Test
    fun `те же сценарии на превью`() =
        runTest {
            scenarios.forEach { scenario ->
                val client = fixture.insertClient()
                val sessionDate = scenario.setup(client)
                val session = debits.session(sessionDate, status = "scheduled")
                debits.mark(session, client, null)
                val state =
                    fixture.tx { context(fixture.ctx) { preview.preview(session) } }
                        .participants
                        .single { it.clientId == client }
                        .debit
                val picked =
                    when (state) {
                        is WillDebitParticipantDebitSchema -> state.membership.name
                        NoMembershipParticipantDebitSchema -> null
                        else -> error("Неожиданное состояние $state")
                    }
                assertEquals(scenario.expected, picked, scenario.name)
                TestPostgres.db.sql("DELETE FROM memberships").execute()
            }
        }
}
