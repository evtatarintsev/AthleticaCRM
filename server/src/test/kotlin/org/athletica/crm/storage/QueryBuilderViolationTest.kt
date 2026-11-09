package org.athletica.crm.storage

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** Проверяет перевод нарушений ограничений в ошибки [QueryBuilder]. */
class QueryBuilderViolationTest {
    @BeforeTest
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Вставляет пользователя с логином [login]; [mapped] — переводить ли дубль логина в доменную ошибку. */
    private suspend fun insertUser(login: String, mapped: Boolean): Either<AppError, Long> =
        either {
            val query =
                TestPostgres.db
                    .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, 'hash')")
                    .bind("id", Uuid.generateV7())
                    .bind("login", login)
            if (mapped) {
                query.onViolation("users_login_key") { CommonDomainError("LOGIN_TAKEN", "Логин занят") }
            }
            query.execute()
        }

    @Test
    fun `зарегистрированное нарушение становится доменной ошибкой`() =
        runBlocking {
            insertUser("a@example.com", mapped = true)

            val error = assertIs<Either.Left<AppError>>(insertUser("a@example.com", mapped = true)).value

            assertEquals(CommonDomainError("LOGIN_TAKEN", "Логин занят"), error)
        }

    @Test
    fun `незарегистрированное нарушение становится ConstraintViolation с именем ограничения`() =
        runBlocking {
            insertUser("a@example.com", mapped = false)

            val error = assertIs<Either.Left<AppError>>(insertUser("a@example.com", mapped = false)).value

            assertEquals("users_login_key", assertIs<ConstraintViolation>(error).constraint)
        }
}
