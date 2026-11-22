package org.athletica.crm.read.widgets

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.widgets.PublicScheduleResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.widgets.DbScheduleWidgets
import org.athletica.crm.domain.widgets.WidgetSettings
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Тесты публичного расписания виджета. Организация во Владивостоке (UTC+10):
 * момент [now] там уже четверг 26.11.2026, поэтому период показа — с понедельника 23.11
 * по 26.12 при горизонте 30 дней.
 */
class DbPublicScheduleViewTest {
    private val view = DbPublicScheduleView()
    private val widgets = DbScheduleWidgets()
    private val fixture = ScheduleFixture(EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_WIDGETS), emptySet()))
    private val now = Instant.parse("2026-11-25T20:00:00Z")
    private val today = LocalDate(2026, 11, 26)
    private val periodStart = LocalDate(2026, 11, 23)
    private val periodEnd = LocalDate(2026, 12, 26)
    private var hall: HallId = HallId.new()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun prepare() {
        fixture.setUp()
        orThrow {
            TestPostgres.db
                .sql("UPDATE organizations SET timezone = 'Asia/Vladivostok' WHERE id = :id")
                .bind("id", fixture.orgId)
                .execute()
        }
        hall = fixture.insertHall("Большой зал")
    }

    /** Создаёт и сохраняет виджет с настройками [settings]; возвращает его публичный ключ. */
    private suspend fun widget(settings: WidgetSettings = WidgetSettings()): Pair<ScheduleWidgetId, String> {
        val id = ScheduleWidgetId.new()
        fixture.inContext { widgets.new(id, testName("Сайт"), settings).save() }
        return id to fixture.inContext { widgets.byId(id).publicKey }
    }

    private suspend fun session(group: GroupId, date: LocalDate, status: String = "scheduled") = fixture.insertSession(group, hall, date, LocalTime(10, 0), LocalTime(11, 0), status = status)

    private suspend fun schedule(key: String, from: LocalDate? = null, to: LocalDate? = null): Either<AppError, PublicScheduleResponse> =
        either {
            val raise: Raise<AppError> = this
            TestPostgres.db.transaction {
                context(this, raise) { view.schedule(PublicScheduleQuery(key, from, to, now, Lang.RU)) }
            }
        }

    private suspend fun succeeding(key: String, from: LocalDate? = null, to: LocalDate? = null) = assertIs<Either.Right<PublicScheduleResponse>>(schedule(key, from, to)).value

    private fun assertUnavailable(result: Either<AppError, *>) {
        assertEquals("WIDGET_UNAVAILABLE", assertIs<CommonDomainError>(assertIs<Either.Left<AppError>>(result).value).code)
    }

    @Test
    fun `период показа считается от сегодняшней даты в часовом поясе организации`() =
        runTest {
            prepare()
            val group = fixture.insertGroup("Взрослые")
            listOf(LocalDate(2026, 11, 22), periodStart, periodEnd, LocalDate(2026, 12, 27)).forEach { session(group, it) }
            val (_, key) = widget()

            val result = succeeding(key)

            assertEquals(today, result.today)
            assertEquals(periodStart, result.periodStart)
            assertEquals(periodEnd, result.periodEnd)
            assertEquals(listOf(periodStart, periodEnd), result.sessions.map { it.date })
        }

    @Test
    fun `запрос за пределами периода обрезается до периода показа`() =
        runTest {
            prepare()
            val group = fixture.insertGroup("Взрослые")
            listOf(LocalDate(2026, 11, 16), periodStart, LocalDate(2026, 11, 30), LocalDate(2027, 1, 4)).forEach { session(group, it) }
            val (_, key) = widget()

            assertEquals(listOf(periodStart), succeeding(key, LocalDate(2026, 11, 16), LocalDate(2026, 11, 22).plus(7)).sessions.map { it.date })
            assertEquals(emptyList(), succeeding(key, LocalDate(2027, 1, 1), LocalDate(2027, 1, 7)).sessions)
        }

    @Test
    fun `исключённые, скрытые и чужие группы не попадают в виджет`() =
        runTest {
            prepare()
            val adults = fixture.insertGroup("Взрослые")
            val kids = fixture.insertGroup("Дети")
            val hidden = fixture.insertGroup("Тестовая")
            orThrow {
                TestPostgres.db.sql("UPDATE groups SET shown_in_widgets = false WHERE id = :id").bind("id", hidden).execute()
            }
            listOf(adults, kids, hidden).forEach { session(it, today) }
            val otherBranch = Uuid.generateV7()
            val northern = GroupId.new()
            orThrow {
                TestPostgres.db.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Север')")
                    .bind("id", otherBranch).bind("orgId", fixture.orgId).execute()
                TestPostgres.db.sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Северные')")
                    .bind("id", northern).bind("orgId", fixture.orgId).bind("branchId", otherBranch).execute()
            }
            session(northern, today)
            val (_, site) = widget()
            val (_, vkKids) = widget(WidgetSettings(excludedGroups = listOf(adults)))

            assertEquals(listOf("Взрослые", "Дети"), succeeding(site).sessions.map { it.groupName })
            assertEquals(listOf("Дети"), succeeding(vkKids).sessions.map { it.groupName })

            orThrow {
                TestPostgres.db.sql("UPDATE groups SET shown_in_widgets = true WHERE id = :id").bind("id", hidden).execute()
            }
            assertEquals(listOf("Взрослые", "Дети", "Тестовая"), succeeding(site).sessions.map { it.groupName })
        }

    @Test
    fun `отменённое занятие остаётся с пометкой`() =
        runTest {
            prepare()
            val group = fixture.insertGroup("Взрослые")
            session(group, today, status = "cancelled")
            session(group, today.plus(1))
            val (_, key) = widget()

            assertEquals(listOf(true, false), succeeding(key).sessions.map { it.cancelled })
        }

    @Test
    fun `выключенные поля приходят null, а тренеры — с учётом замены`() =
        runTest {
            prepare()
            val group = fixture.insertGroup("Взрослые")
            orThrow {
                val id = Uuid.generateV7()
                TestPostgres.db.sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, 'Йога')")
                    .bind("id", id).bind("orgId", fixture.orgId).execute()
                TestPostgres.db.sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:g, :d)")
                    .bind("g", group).bind("d", id).execute()
            }
            val anna = fixture.insertEmployee("Анна")
            val boris = fixture.insertEmployee("Борис")
            fixture.linkGroupEmployee(group, anna)
            val replaced = fixture.insertSession(group, hall, today, LocalTime(10, 0), LocalTime(11, 0), isEmployeeAssignmentOverridden = true)
            fixture.linkSessionEmployee(replaced, boris)
            val (_, byDefault) = widget()
            val (_, everything) = widget(WidgetSettings(showCoaches = true))
            val (_, nothing) = widget(WidgetSettings(showHall = false, showDisciplines = false))

            val defaultCard = succeeding(byDefault).sessions.single()
            assertNull(defaultCard.coaches)
            assertEquals("Большой зал", defaultCard.hall)
            assertEquals(listOf("Йога"), defaultCard.disciplines)
            assertEquals(listOf("Борис"), succeeding(everything).sessions.single().coaches)
            val bareCard = succeeding(nothing).sessions.single()
            assertNull(bareCard.hall)
            assertNull(bareCard.disciplines)
            assertNull(bareCard.coaches)
        }

    @Test
    fun `неизвестный, выключенный и удалённый виджет недоступны одинаково`() =
        runTest {
            prepare()
            val (_, disabled) = widget(WidgetSettings(enabled = false))
            val (deletedId, deleted) = widget()
            fixture.inContext { widgets.byId(deletedId).delete() }
            val (regeneratedId, old) = widget()
            fixture.inContext { widgets.byId(regeneratedId).regenerateKey() }

            assertUnavailable(schedule("unknown-key"))
            assertUnavailable(schedule(disabled))
            assertUnavailable(schedule(deleted))
            assertUnavailable(schedule(old))
            succeeding(fixture.inContext { widgets.byId(regeneratedId).publicKey })
        }
}

/** Прибавляет [days] дней к дате. */
private fun LocalDate.plus(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
