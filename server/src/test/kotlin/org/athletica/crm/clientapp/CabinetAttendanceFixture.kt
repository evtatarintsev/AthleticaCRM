package org.athletica.crm.clientapp

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Помощники DB-тестов расписания кабинета: организация журнала посещаемости с включённым кабинетом
 * и аккаунт мамы с номером [MOM], подключённым к клиентам организации.
 */
class CabinetAttendanceFixture {
    /** Организация с залом, группой и занятиями — часовой пояс UTC. */
    val attendance = AttendanceFixture()

    /** Аккаунт мамы; создаётся в [setUp]. */
    var account: ClientAccountContext = ClientAppFixture.accountContext(ClientAppAccountId.new())
        private set

    /** Организация фикстуры. */
    val orgId: OrgId get() = attendance.schedule.orgId.toOrgId()

    /** Создаёт организацию с включённым кабинетом и аккаунт мамы. */
    suspend fun setUp() {
        attendance.setUp()
        orThrow {
            TestPostgres.db.sql("UPDATE organizations SET client_app_enabled = true WHERE id = :id").bind("id", orgId).execute()
            account = ClientAppFixture.accountContext(ClientAppFixture.account(MOM))
        }
    }

    /** Клиент [name] с номером мамы и доступом в статусе [status]. */
    suspend fun client(
        name: String = "Иван",
        status: ClientAppAccessStatus = ClientAppAccessStatus.CONNECTED,
    ): ClientId = orThrow { ClientAppFixture.client(orgId, MOM, status, name = name) }

    /** Выполняет [block] от имени профиля клиента [clientId] глазами аккаунта мамы. */
    suspend fun <T> asMom(
        clientId: ClientId,
        block: suspend context(ClientProfileContext, Transaction, Raise<AppError>) () -> T,
    ): Either<AppError, T> =
        either {
            TestPostgres.db.transaction { context(account) { context(ClientProfileContexts.profile(orgId, clientId)) { block() } } }
        }

    /** Момент [time] даты [date] в часовом поясе организации (UTC). */
    fun at(
        date: LocalDate,
        time: LocalTime,
    ): Instant = LocalDateTime(date, time).toInstant(TimeZone.UTC)

    companion object {
        /** Номер мамы. */
        const val MOM = "+7 903 123-45-67"
    }
}
