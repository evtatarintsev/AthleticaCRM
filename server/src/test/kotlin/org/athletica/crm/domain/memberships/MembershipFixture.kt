package org.athletica.crm.domain.memberships

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Тестовое окружение абонементов: организация в часовом поясе [timezone] и валюте [currency],
 * выдающий сотрудник, контексты сотрудника и системы, подменяемые часы.
 */
class MembershipFixture(
    private val timezone: String = "UTC",
    private val currency: Currency = Currency.RUB,
) {
    /** Организация теста. */
    val orgId: OrgId = OrgId.new()

    /** Сотрудник, выдающий абонементы. */
    val employeeId: EmployeeId = EmployeeId.new()

    /** Контекст выдающего сотрудника. */
    val ctx: EmployeeRequestContext = employeeContext(orgId = orgId, employeeId = employeeId, currency = currency)

    /** Системный контекст организации. */
    val system: SystemRequestContext = SystemRequestContext(orgId = orgId, currency = currency)

    /** Хранилище абонементов. */
    val memberships = DbMemberships()

    /** Подменяемые часы: «сейчас» задаётся через [at]. */
    val clock = MutableClock(Instant.parse("2026-10-16T09:00:00Z"))

    /** Создаёт организацию и выдающего сотрудника. */
    suspend fun setUp() {
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name, timezone, currency) VALUES (:id, 'Org', :tz, :currency)")
            .bind("id", orgId)
            .bind("tz", timezone)
            .bind("currency", currency.name)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, org_id, name, is_owner) VALUES (:id, :orgId, 'Админ', true)")
            .bind("id", employeeId)
            .bind("orgId", orgId)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Центр')")
            .bind("id", branchId)
            .bind("orgId", orgId)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Дети 7–9')")
            .bind("id", groupId)
            .bind("orgId", orgId)
            .bind("branchId", branchId)
            .execute()
    }

    /** Ставит часы на полдень даты [date] в часовом поясе организации. */
    fun at(date: LocalDate) {
        clock.now = date.atTime(LocalTime(12, 0)).toInstant(TimeZone.of(timezone))
    }

    /** Филиал организации для группы. */
    val branchId: BranchId = BranchId.new()

    /** Группа организации, создаётся в [setUp]; группа абонементов по умолчанию. */
    val groupId: GroupId = GroupId.new()

    /** Создаёт клиента организации. */
    suspend fun insertClient(): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, 'Иван', 'MALE'::gender)")
            .bind("id", clientId)
            .bind("orgId", orgId)
            .execute()
        return clientId
    }

    /** Выполняет [block] в транзакции в контексте [context]; ошибка домена — провал теста. */
    suspend fun <T> tx(
        context: RequestContext = ctx,
        block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T,
    ): T = attempt(context, block).getOrElse { fail("Unexpected error: $it") }

    /** Выполняет [block] в транзакции в контексте [context] и возвращает результат или ошибку домена. */
    suspend fun <T> attempt(
        context: RequestContext = ctx,
        block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T,
    ): Either<DomainError, T> =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction { block(context, this, raise) }
        }

    /** Выдаёт абонемент клиенту [clientId] от имени сотрудника в дату [issuedOn]. */
    suspend fun issue(
        clientId: ClientId,
        activation: Activation = Activation.Immediate,
        closeMode: CloseMode = CloseMode.MANUAL,
        sessions: Int? = 8,
        duration: MembershipDuration = MembershipDuration(1, DurationUnit.MONTHS),
        issuedOn: LocalDate = LocalDate(2026, 10, 16),
        name: String = "8 занятий",
        groupIds: List<GroupId> = listOf(groupId),
    ): Membership =
        tx {
            memberships.issue(
                MembershipId.new(),
                clientId,
                null,
                name,
                sessions,
                groupIds,
                duration,
                Money(4_000_00, currency),
                activation,
                closeMode,
                issuedOn,
            )
        }

    /** Читает абонемент [id] в системном контексте. */
    suspend fun reload(id: MembershipId): Membership = tx(system) { memberships.byId(id) }

    /** Устанавливает остаток занятий абонемента [id] напрямую в БД. */
    suspend fun setRemaining(id: MembershipId, remaining: Int) {
        TestPostgres.db
            .sql("UPDATE memberships SET sessions_remaining = :r WHERE id = :id")
            .bind("r", remaining)
            .bind("id", id)
            .execute()
    }

    /** Виды записей истории абонемента [id] от старых к новым. */
    suspend fun eventKinds(id: MembershipId): List<String> =
        TestPostgres.db
            .sql("SELECT kind FROM membership_events WHERE membership_id = :id ORDER BY occurred_at, id")
            .bind("id", id)
            .list { it.get("kind", String::class.java)!! }
}

/** Часы с изменяемым «сейчас» [now]. */
class MutableClock(
    /** Текущий момент. */
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}
