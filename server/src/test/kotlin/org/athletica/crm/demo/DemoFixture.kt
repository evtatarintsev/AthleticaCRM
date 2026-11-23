package org.athletica.crm.demo

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.org.dateIn
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.domain.tasks.Task
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.testDi
import org.athletica.crm.testName
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Помощник тестов наполнения: регистрация организации, наполнение и обработка событий после коммита. */
object DemoFixture {
    /** Часовой пояс тестовых организаций. */
    const val TIMEZONE = "Asia/Yekaterinburg"

    /** Сотрудники для сборки контекста владельца. */
    private val employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles())

    /** Обработчики событий и планировщик абонементов, как в запущенном сервере. */
    private val di by lazy { testDi() }

    /** Хранилище задач, отказывающее при создании задачи, — сбой посередине наполнения. */
    val failingTasks: Tasks =
        object : Tasks by DbTasks() {
            context(ctx: RequestContext, tr: Transaction, raise: arrow.core.raise.context.Raise<AppError>)
            override suspend fun new(
                id: TaskId,
                title: NonBlankName,
                description: String,
                clientId: ClientId?,
                dueDate: Instant?,
                dueDateEnd: Instant?,
            ): Task = raise(CommonDomainError("BOOM", "сбой хранилища задач"))
        }

    /** Сегодняшняя дата тестовых организаций. */
    fun today(): LocalDate = dateIn(TIMEZONE, Clock.System.now())

    /** Регистрирует организацию через [org.athletica.crm.domain.auth.DbAuth.signup] и возвращает её идентификатор. */
    suspend fun signup(name: String = "Клуб"): OrgId =
        orThrow {
            TestPostgres.db.transaction {
                TestPostgres.auth.signup(
                    SignUpRequest(testName(name), testName("Владелец"), "owner-${Uuid.random()}@example.com", "password123", TIMEZONE, Currency.RUB),
                    Lang.RU,
                    null,
                )
            }.orgId
        }

    /**
     * Наполняет организацию [orgId] профилем [profile] с зерном [seed] на дату [today] и историей [history]
     * одной транзакцией от имени владельца; [demoData] подменяется в тестах на отказ.
     */
    suspend fun fill(
        orgId: OrgId,
        profile: DemoProfile = DemoProfile.SMALL,
        seed: Int = 1,
        today: LocalDate = today(),
        history: DatePeriod = DatePeriod(months = profile.historyMonths),
        demoData: DemoData = DemoData(),
    ): Either<AppError, DemoSummary> =
        either {
            TestPostgres.db.transaction {
                context(ownerContext(orgId, employees)) {
                    demoData.fill(profile, Random(seed), today, history)
                }
            }
        }

    /** Выполняет [block] в транзакции от имени владельца организации [orgId]; ошибка домена роняет тест. */
    suspend fun <T> asOwner(orgId: OrgId, block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T): T =
        orThrow {
            val raise: Raise<AppError> = this
            TestPostgres.db.transaction {
                context(ownerContext(orgId, employees), this, raise) { block() }
            }
        }

    /** Обрабатывает опубликованные события обработчиками списаний и прогоняет планировщик абонементов. */
    suspend fun processAfterCommit() {
        while (pendingEvents() > 0) {
            di.eventWorker.processBatch()
        }
        di.membershipScheduler.processDue()
    }

    /** Число событий, ожидающих обработки прямо сейчас. */
    private suspend fun pendingEvents(): Long =
        orThrow {
            TestPostgres.db.sql("SELECT count(*) AS n FROM domain_events WHERE processed_at IS NULL AND failed_at IS NULL AND next_attempt_at <= now()")
                .firstOrNull { it.asLong("n") } ?: 0
        }
}
