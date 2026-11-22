package org.athletica.crm.demo

import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toSaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.Remaining
import org.athletica.crm.core.subscription.TermPosition
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.DbMemberships
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.audit.ActivityLogQuery
import org.athletica.crm.read.clients.ClientListQuery
import org.athletica.crm.read.finance.CashFlowReportQuery
import org.athletica.crm.read.finance.CashJournalQuery
import org.athletica.crm.read.finance.SaleDetailQuery
import org.athletica.crm.read.groups.GroupListQuery
import org.athletica.crm.read.schedule.ScheduleQuery
import org.athletica.crm.read.tasks.TaskListQuery
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Наполнение организации профилем `small` и правила наполнения.
 *
 * Полное наполнение выполняется один раз на класс: тесты только читают его организацию.
 * Тесты правил (пустая организация, профили, детерминизм, атомарность) регистрируют свои
 * организации рядом и проверяют только их, поэтому база между тестами не очищается.
 */
class DemoDataTest {
    /** Наполненная организация [orgId] на дату [today]; [startedAt] — момент перед наполнением. */
    private class Filled(
        /** Организация. */
        val orgId: OrgId,
        /** Сегодняшняя дата наполнения. */
        val today: LocalDate,
        /** Момент перед началом наполнения. */
        val startedAt: Instant,
        /** Итог наполнения. */
        val summary: DemoSummary,
        /** Колонки технических времён, в которых до обработки событий нашлись строки момента наполнения. */
        val lateColumns: Map<String, Long>,
    ) {
        /** Первый день периода истории. */
        val start: LocalDate = today.minus(DatePeriod(months = DemoProfile.SMALL.historyMonths))
    }

    private companion object {
        /** Организация, наполненная один раз на класс, после обработки событий и планировщика. */
        val filled: Filled by lazy {
            runBlocking {
                TestPostgres.truncate()
                val today = DemoFixture.today()
                val orgId = DemoFixture.signup()
                val startedAt = Clock.System.now()
                val summary = DemoFixture.fill(orgId, today = today).getOrElse { error(it) }
                val late = lateColumns(orgId, startedAt)
                DemoFixture.processAfterCommit()
                Filled(orgId, today, startedAt, summary, late)
            }
        }

        /**
         * Колонки [DemoTimestamps.columns] со строками организации [orgId], датированными не раньше [at];
         * считается сразу после наполнения, до записей обработчиков событий и планировщика.
         */
        suspend fun lateColumns(orgId: OrgId, at: Instant): Map<String, Long> {
            val parents = mapOf("session_attendance" to ("sessions" to "session_id"), "enrollments" to ("groups" to "group_id"))
            return DemoTimestamps.columns.associate { (table, column) ->
                val parent = parents[table]
                val sql =
                    if (parent == null) {
                        "SELECT count(*) AS n FROM $table WHERE org_id = :org AND $column >= :at"
                    } else {
                        "SELECT count(*) AS n FROM $table c JOIN ${parent.first} p ON p.id = c.${parent.second} WHERE p.org_id = :org AND c.$column >= :at"
                    }
                "$table.$column" to orThrow { TestPostgres.db.sql(sql).bind("org", orgId).bind("at", at).firstOrNull { it.asLong("n") } ?: 0 }
            }
        }

        /** Проекции чтения. */
        val views = ReadViews()
    }

    /** Значение `n` запроса [sql] с параметрами `:org`, `:today`, `:start` наполненной организации. */
    private suspend fun num(sql: String, orgId: OrgId = filled.orgId): Long =
        orThrow {
            TestPostgres.db.sql(sql).bind("org", orgId)
                .let { if (":today" in sql) it.bind("today", filled.today) else it }
                .let { if (":start" in sql) it.bind("start", filled.start) else it }
                .firstOrNull { it.asLong("n") } ?: 0
        }

    /** Клиенты организации [orgId]. */
    private suspend fun clientIds(orgId: OrgId = filled.orgId): List<ClientId> = orThrow { TestPostgres.db.sql("SELECT id FROM clients WHERE org_id = :org ORDER BY id").bind("org", orgId).list { it.asUuid("id").toClientId() } }

    /** Выполняет [block] от имени владельца наполненной организации. */
    private suspend fun <T> asOwner(block: suspend context(org.athletica.crm.core.EmployeeRequestContext, Transaction, arrow.core.raise.Raise<AppError>) () -> T): T = DemoFixture.asOwner(filled.orgId, block)

    /** Все абонементы наполненной организации. */
    private suspend fun allMemberships(): List<Membership> {
        val ids = clientIds()
        return asOwner { ids.flatMap { DbMemberships().forClient(it) } }
    }

    @Test
    fun `структура и справочники созданы, войти можно только владельцем`() =
        runTest(timeout = 10.minutes) {
            assertTrue(num("SELECT count(*) AS n FROM branches WHERE org_id = :org") >= 2)
            assertTrue(num("SELECT count(*) AS n FROM disciplines WHERE org_id = :org") > 0)
            assertTrue(num("SELECT count(*) AS n FROM subscription_tariffs WHERE org_id = :org") > 0)
            assertTrue(num("SELECT count(*) AS n FROM roles WHERE org_id = :org") > 0)
            assertTrue(num("SELECT count(*) AS n FROM custom_field_definitions WHERE org_id = :org") > 0)
            assertEquals(1, num("SELECT count(*) AS n FROM employees WHERE org_id = :org AND user_id IS NOT NULL"))
            assertTrue(num("SELECT count(*) AS n FROM employees WHERE org_id = :org AND user_id IS NULL") > 0)
        }

    @Test
    fun `клиенты приходят в течение всей истории`() =
        runTest(timeout = 10.minutes) {
            val third = filled.start.daysUntil(filled.today) / 3
            val days = "(SELECT (c.created_at AT TIME ZONE o.timezone)::date AS d FROM clients c JOIN organizations o ON o.id = c.org_id WHERE c.org_id = :org) x"
            assertTrue(num("SELECT count(*) AS n FROM $days WHERE d < :start::date + $third") > 0)
            assertTrue(num("SELECT count(*) AS n FROM $days WHERE d > :today::date - $third") > 0)
            assertEquals(0, num("SELECT count(*) AS n FROM $days WHERE d < :start::date OR d > :today::date"))
            assertEquals(1, num("SELECT count(*) AS n FROM (SELECT min(d) AS m FROM $days) y WHERE m = :start::date"))
            assertEquals(1, num("SELECT count(*) AS n FROM (SELECT max(d) AS m FROM $days) y WHERE m = :today::date"))
        }

    @Test
    fun `есть клиент без групп и клиент во всех группах своего филиала`() =
        runTest(timeout = 10.minutes) {
            asOwner { assertTrue(views.clientList.page(ClientListQuery(noGroup = true)).total > 0u) }
            val inAllGroups =
                num(
                    """
                    SELECT count(*) AS n FROM clients c WHERE c.org_id = :org AND EXISTS (
                        SELECT 1 FROM groups g WHERE g.org_id = :org GROUP BY g.branch_id
                        HAVING count(*) >= 2 AND bool_and(EXISTS (SELECT 1 FROM enrollments e
                            WHERE e.group_id = g.id AND e.client_id = c.id AND e.left_at IS NULL)))
                    """.trimIndent(),
                )
            assertTrue(inAllGroups > 0)
        }

    @Test
    fun `расписание продолжается на сегодня и ближайшие недели`() =
        runTest(timeout = 10.minutes) {
            assertTrue(num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND date = :today") > 0)
            assertTrue(num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND date > :today AND date <= :today::date + 21") > 0)
        }

    @Test
    fun `основные экраны возвращают непустые данные`() =
        runTest(timeout = 10.minutes) {
            val today = filled.today
            val start = filled.start
            val saleId = orThrow { TestPostgres.db.sql("SELECT id FROM sales WHERE org_id = :org LIMIT 1").bind("org", filled.orgId).firstOrNull { it.asUuid("id").toSaleId() } }
            checkNotNull(saleId)
            asOwner {
                assertTrue(views.clientList.page(ClientListQuery()).total > 0u, "клиенты")
                val groups = views.groupList.list(GroupListQuery()).groups
                assertTrue(groups.isNotEmpty(), "группы")
                groups.forEach { views.groupDetail.byId(it.id) }
                assertTrue(views.todaySchedule.sessionsOn(today).sessions.isNotEmpty(), "сегодня")
                assertTrue(views.schedule.list(ScheduleQuery(today, today.plus(7, DateTimeUnit.DAY))).sessions.isNotEmpty(), "расписание")
                assertTrue(views.cashFlowReport.report(CashFlowReportQuery(start, today)).receipts.isPositive, "движение денег")
                val journal = CashJournalQuery(start, today, emptyList(), emptyList(), listOf(LedgerKind.PAYMENT), null, null, false, 50, 0, today)
                assertTrue(views.cashJournal.page(journal).count > 0, "журнал операций")
                views.saleDetail.byId(SaleDetailQuery(saleId, today))
                assertTrue(views.activityLog.count(ActivityLogQuery(limit = 50u, offset = 0u)) > 0, "журнал действий")
            }
        }

    @Test
    fun `у группы с начала истории проведены занятия за несколько месяцев с журналами`() =
        runTest(timeout = 10.minutes) {
            val groupId =
                orThrow {
                    TestPostgres.db.sql(
                        """
                        SELECT g.id FROM groups g JOIN sessions s ON s.group_id = g.id
                        JOIN session_attendance a ON a.session_id = s.id
                        WHERE g.org_id = :org GROUP BY g.id ORDER BY min(s.date), count(*) DESC LIMIT 1
                        """.trimIndent(),
                    ).bind("org", filled.orgId).firstOrNull { it.asUuid("id").toGroupId() }
                }
            checkNotNull(groupId)
            val group = "s.org_id = :org AND s.group_id = '$groupId'"
            assertTrue(num("SELECT (max(date) - min(date))::bigint AS n FROM sessions s WHERE $group AND status = 'completed'") >= 60)
            assertEquals(0, num("SELECT count(*) AS n FROM sessions s WHERE $group AND status = 'completed' AND NOT EXISTS (SELECT 1 FROM session_attendance a WHERE a.session_id = s.id)"))
        }

    @Test
    fun `незакрытые журналы есть только среди последних дней, отметок в будущем нет`() =
        runTest(timeout = 10.minutes) {
            assertTrue(num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND status = 'scheduled' AND date < :today") > 0)
            assertEquals(0, num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND status = 'scheduled' AND date < :today::date - 3"))
            assertTrue(num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND status = 'cancelled' AND date < :today") > 0)
            assertEquals(0, num("SELECT count(*) AS n FROM session_attendance a JOIN sessions s ON s.id = a.session_id WHERE s.org_id = :org AND s.date > :today"))
            assertEquals(0, num("SELECT count(*) AS n FROM sessions WHERE org_id = :org AND status <> 'scheduled' AND date >= :today"))
        }

    @Test
    fun `абонементы продлевались и есть во всех пяти состояниях`() =
        runTest(timeout = 10.minutes) {
            val today = filled.today
            val all = allMemberships()
            val renewed =
                all.groupBy { it.clientId }.values.any { list ->
                    list.size >= 2 && list.sortedBy { it.issuedOn }.dropLast(1).all { it is ClosedMembership || (it is ActiveMembership && it.statusAt(today).closePending) }
                }
            assertTrue(renewed, "нет клиента с последовательными абонементами")
            val active = all.filterIsInstance<ActiveMembership>()
            assertTrue(active.any { it.statusAt(today).termPosition == TermPosition.IN_TERM && it.statusAt(today).remaining != Remaining.Exhausted && it.freezeAt(today) == null }, "активный")
            assertTrue(all.any { it is InactiveMembership }, "неактивированный")
            assertTrue(all.any { (it is ClosedMembership && it.closeReason == CloseReason.LAST_SESSION) || (it is ActiveMembership && it.statusAt(today).remaining == Remaining.Exhausted) }, "исчерпанный")
            assertTrue(all.any { (it is ClosedMembership && it.closeReason == CloseReason.EXPIRY) || (it is ActiveMembership && it.statusAt(today).termPosition == TermPosition.EXPIRED) }, "истёкший")
            assertTrue(active.any { it.freezeAt(today) != null }, "замороженный")
        }

    @Test
    fun `каждое посещение с подходящим абонементом списано, остатки не отрицательны`() =
        runTest(timeout = 10.minutes) {
            assertEquals(0, num("SELECT count(*) AS n FROM domain_events WHERE org_id = :org AND (failed_at IS NOT NULL OR retry_count > 0 OR processed_at IS NULL)"))
            assertTrue(num("SELECT count(*) AS n FROM membership_debits WHERE org_id = :org AND reversed_at IS NULL") > 0)
            assertEquals(0, num("SELECT count(*) AS n FROM memberships WHERE org_id = :org AND sessions_remaining < 0"))
            assertEquals(
                0,
                num(
                    """
                    SELECT count(*) AS n FROM session_attendance a JOIN sessions s ON s.id = a.session_id
                    WHERE s.org_id = :org AND s.status = 'completed' AND a.presence = 'present'
                      AND EXISTS (SELECT 1 FROM memberships m JOIN membership_groups mg ON mg.membership_id = m.id
                                  WHERE m.client_id = a.client_id AND mg.group_id = s.group_id
                                    AND s.date BETWEEN m.start_date AND m.end_date)
                      AND NOT EXISTS (SELECT 1 FROM membership_debits d
                                      WHERE d.session_id = s.id AND d.client_id = a.client_id AND d.reversed_at IS NULL)
                    """.trimIndent(),
                ),
            )
        }

    @Test
    fun `есть ушедший клиент`() =
        runTest(timeout = 10.minutes) {
            val today = filled.today
            val churned =
                orThrow {
                    TestPostgres.db.sql(
                        """
                        SELECT c.id FROM clients c WHERE c.org_id = :org
                          AND EXISTS (SELECT 1 FROM session_attendance a JOIN sessions s ON s.id = a.session_id
                                      WHERE a.client_id = c.id AND a.presence = 'present' AND s.date < :start::date + 40)
                          AND NOT EXISTS (SELECT 1 FROM session_attendance a JOIN sessions s ON s.id = a.session_id
                                          WHERE a.client_id = c.id AND a.presence = 'present' AND s.date > :today::date - 30)
                        """.trimIndent(),
                    ).bind("org", filled.orgId).bind("start", filled.start).bind("today", today).list { it.asUuid("id").toClientId() }
                }
            val memberships = asOwner { churned.associateWith { DbMemberships().forClient(it) } }
            val gone =
                memberships.values.any { list ->
                    list.none { it is InactiveMembership || (it is ActiveMembership && it.statusAt(today).termPosition == TermPosition.IN_TERM) }
                }
            assertTrue(gone, "нет клиента, который ходил в начале истории и ушёл")
        }

    @Test
    fun `оплаты приходят позже продаж, есть должники и авансы`() =
        runTest(timeout = 10.minutes) {
            assertTrue(
                num("SELECT count(*) AS n FROM ledger_transactions t JOIN sales s ON s.id = t.sale_id WHERE t.org_id = :org AND t.kind = 'payment' AND t.occurred_on > s.sold_on") > 0,
            )
            val ids = clientIds()
            val balances =
                asOwner {
                    ids.map { id ->
                        val balance = views.clientBalance.byClient(id)
                        val ledger = views.clientLedger.byClient(id)
                        assertEquals(balance, ledger.balance)
                        assertEquals(balance.minorUnits, ledger.entries.sumOf { it.amount.minorUnits })
                        balance
                    }
                }
            assertTrue(balances.any { it.isNegative }, "должник")
            assertTrue(balances.any { it.isZero }, "нулевой баланс")
            assertTrue(balances.any { it.isPositive }, "аванс")
        }

    @Test
    fun `задачи открыты и закрыты`() =
        runTest(timeout = 10.minutes) {
            asOwner {
                fun query(status: TaskStatus) = TaskListQuery(false, setOf(status), null, null, null, null, 50, 0)
                assertTrue(views.taskList.page(query(TaskStatus.PENDING)).total > 0u)
                assertTrue(views.taskList.page(query(TaskStatus.COMPLETED)).total > 0u)
            }
        }

    @Test
    fun `технические времена растянуты по истории, а не сосредоточены в моменте наполнения`() =
        runTest(timeout = 10.minutes) {
            assertTrue(num("SELECT count(DISTINCT (created_at AT TIME ZONE 'UTC')::date) AS n FROM audit_logs WHERE org_id = :org") >= 30)
            assertEquals(emptyMap(), filled.lateColumns.filterValues { it > 0 })
        }

    @Test
    fun `контакты вымышленные, каналов и сообщений нет`() =
        runTest(timeout = 10.minutes) {
            assertEquals(0, num("SELECT count(*) AS n FROM client_contacts WHERE org_id = :org AND type = 'EMAIL' AND value NOT LIKE '%@example.com'"))
            assertEquals(0, num("SELECT count(*) AS n FROM client_contacts WHERE org_id = :org AND type = 'PHONE' AND value NOT LIKE '+7 000%'"))
            assertEquals(0, num("SELECT count(*) AS n FROM employees WHERE org_id = :org AND NOT is_owner AND (email NOT LIKE '%@example.com' OR phone_no NOT LIKE '+7 000%')"))
            assertEquals(0, num("SELECT count(*) AS n FROM channel_integrations WHERE org_id = :org"))
            assertEquals(0, num("SELECT count(*) AS n FROM conversations WHERE org_id = :org"))
            assertEquals(0, num("SELECT count(*) AS n FROM messages WHERE org_id = :org"))
        }

    @Test
    fun `история small не короче трёх месяцев, деловые даты не позже сегодня`() =
        runTest(timeout = 10.minutes) {
            val threeMonthsAgo = filled.today.minus(DatePeriod(months = 3))
            val earliest = orThrow { TestPostgres.db.sql("SELECT min(date) AS d FROM sessions WHERE org_id = :org AND status = 'completed'").bind("org", filled.orgId).firstOrNull { it.asLocalDate("d") } }
            assertTrue(checkNotNull(earliest) <= threeMonthsAgo)
            assertEquals(0, num("SELECT count(*) AS n FROM sales WHERE org_id = :org AND sold_on > :today"))
            assertEquals(0, num("SELECT count(*) AS n FROM ledger_transactions WHERE org_id = :org AND occurred_on > :today"))
            assertEquals(0, num("SELECT count(*) AS n FROM sales WHERE org_id = :org AND sold_on < :start"))
            assertEquals(filled.summary.sales.toLong(), num("SELECT count(*) AS n FROM sales WHERE org_id = :org"))
        }

    @Test
    fun `организация с клиентом или группой не наполняется`() =
        runTest(timeout = 10.minutes) {
            filled
            val withClient = DemoFixture.signup("С клиентом")
            orThrow {
                TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :org, 'Клиент', 'MALE')").bind("id", ClientId.new()).bind("org", withClient).execute()
            }
            val withGroup = DemoFixture.signup("С группой")
            orThrow {
                TestPostgres.db.sql("INSERT INTO groups (org_id, name, branch_id) SELECT :org, 'Группа', id FROM branches WHERE org_id = :org").bind("org", withGroup).execute()
            }
            listOf(withClient, withGroup).forEach { orgId ->
                val counts = listOf("clients", "groups", "branches").map { num("SELECT count(*) AS n FROM $it WHERE org_id = :org", orgId) }
                val error = assertIs<Either.Left<AppError>>(DemoFixture.fill(orgId)).value
                assertIs<CommonDomainError>(error)
                assertEquals("DEMO_ORG_NOT_EMPTY", error.code)
                assertEquals(counts, listOf("clients", "groups", "branches").map { num("SELECT count(*) AS n FROM $it WHERE org_id = :org", orgId) })
            }
        }

    @Test
    fun `профили упорядочены по объёму, large даёт не меньше тысячи клиентов`() =
        runTest(timeout = 10.minutes) {
            filled
            val short = DatePeriod(days = 14)
            val small = DemoFixture.signup("Small")
            val medium = DemoFixture.signup("Medium")
            DemoFixture.fill(small, DemoProfile.SMALL, history = short).getOrElse { error(it) }
            DemoFixture.fill(medium, DemoProfile.MEDIUM, history = short).getOrElse { error(it) }
            listOf("clients", "groups").forEach { table ->
                assertTrue(num("SELECT count(*) AS n FROM $table WHERE org_id = :org", medium) > num("SELECT count(*) AS n FROM $table WHERE org_id = :org", small), table)
            }
            assertTrue(DemoProfile.MEDIUM.historyMonths >= DemoProfile.SMALL.historyMonths)
            assertTrue(DemoProfile.SMALL.historyMonths >= 3)

            val large = DemoFixture.signup("Large")
            DemoFixture.fill(large, DemoProfile.LARGE, history = DatePeriod(days = 7)).getOrElse { error(it) }
            assertTrue(num("SELECT count(*) AS n FROM clients WHERE org_id = :org", large) >= 1000)
        }

    @Test
    fun `одно зерно, профиль и дата дают одинаковые данные`() =
        runTest(timeout = 10.minutes) {
            filled
            val history = DatePeriod(days = 21)
            val first = DemoFixture.signup("Первая")
            val second = DemoFixture.signup("Вторая")
            DemoFixture.fill(first, seed = 5, history = history).getOrElse { error(it) }
            DemoFixture.fill(second, seed = 5, history = history).getOrElse { error(it) }
            listOf(
                "SELECT name AS v FROM clients WHERE org_id = :org",
                "SELECT name AS v FROM groups WHERE org_id = :org",
                "SELECT s.sold_on || ' ' || sum(i.amount) AS v FROM sales s JOIN sale_items i ON i.sale_id = s.id WHERE s.org_id = :org GROUP BY s.id",
                "SELECT t.occurred_on || ' ' || sum(p.amount) FILTER (WHERE p.amount > 0) AS v FROM ledger_transactions t JOIN ledger_postings p ON p.transaction_id = t.id WHERE t.org_id = :org AND t.kind = 'payment' GROUP BY t.id",
            ).forEach { sql ->
                fun values(orgId: OrgId) = runBlocking { orThrow { TestPostgres.db.sql(sql).bind("org", orgId).list { it.asString("v") } } }.sorted()
                val expected = values(first)
                assertTrue(expected.isNotEmpty(), sql)
                assertEquals(expected, values(second), sql)
            }
        }

    @Test
    fun `ошибка посередине оставляет организацию пустой`() =
        runTest(timeout = 10.minutes) {
            filled
            val orgId = DemoFixture.signup("Сбой")
            val result = DemoFixture.fill(orgId, history = DatePeriod(days = 21), demoData = DemoData(tasks = DemoFixture.failingTasks))
            assertEquals("BOOM", assertIs<CommonDomainError>(assertIs<Either.Left<AppError>>(result).value).code)
            listOf("clients", "groups", "sales", "sessions", "tasks").forEach { table ->
                assertEquals(0, num("SELECT count(*) AS n FROM $table WHERE org_id = :org", orgId), table)
            }
            assertEquals(0, num("SELECT count(*) AS n FROM employees WHERE org_id = :org AND NOT is_owner", orgId))
            assertEquals(1, num("SELECT count(*) AS n FROM branches WHERE org_id = :org", orgId))
        }
}
