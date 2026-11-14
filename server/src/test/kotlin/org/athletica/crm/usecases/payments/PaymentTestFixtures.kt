package org.athletica.crm.usecases.payments

import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString

/** Создаёт организацию в рублях с одним сотрудником и возвращает контекст этого сотрудника. */
suspend fun paymentTestContext(): EmployeeRequestContext =
    orThrow {
        val orgId = OrgId.new()
        val userId = UserId.new()
        val employeeId = EmployeeId.new()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name, currency) VALUES (:id, 'Тестовая организация', 'RUB')")
            .bind("id", orgId)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, 'hash')")
            .bind("id", userId)
            .bind("login", "${userId.value}@test.com")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, org_id, user_id, name) VALUES (:id, :orgId, :userId, 'Тест')")
            .bind("id", employeeId)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .execute()
        employeeContext(orgId = orgId, userId = userId, employeeId = employeeId)
    }

/** Статус платежа [externalPaymentId] или `null`, если записи нет. */
suspend fun paymentStatus(externalPaymentId: String): String? =
    orThrow {
        TestPostgres.db
            .sql("SELECT status FROM payment_transactions WHERE external_payment_id = :id")
            .bind("id", externalPaymentId)
            .firstOrNull { row -> row.asString("status") }
    }

/** Число платежей организации [orgId]. */
suspend fun paymentCount(orgId: OrgId): Long =
    orThrow {
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM payment_transactions WHERE org_id = :orgId")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asLong("cnt") } ?: 0L
    }

/** Число записей журнала баланса организации [orgId]. */
suspend fun orgBalanceEntryCount(orgId: OrgId): Long =
    orThrow {
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM org_balance_journal WHERE org_id = :orgId")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asLong("cnt") } ?: 0L
    }
