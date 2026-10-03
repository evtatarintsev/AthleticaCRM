package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asString
import org.athletica.crm.usecases.auth.SignUpError
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SignUpTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private fun request(login: String = "user@example.com") =
        SignUpRequest(
            companyName = "Acme",
            userName = "John",
            login = login,
            password = "password123",
            timezone = "UTC",
            currency = Currency.RUB,
        )

    @Test
    fun `signUp returns user on success`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            val result = TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request()) } }
            val user = assertIs<Either.Right<User>>(result).value
            assertEquals("user@example.com", user.username)
        }

    @Test
    fun `signUp returns UserAlreadyRegistered when login is taken`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request()) } }
            // signUp поглощает R2DBC-исключение и возвращает Either.Left;
            // PostgreSQL переходит в error-state → commit падает. Захватываем результат до коммита.
            var result: Either<SignUpError, User>? = null
            runCatching {
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { result = signUp(request()) } }
            }
            assertIs<Either.Left<SignUpError.UserAlreadyRegistered>>(result)
        }

    @Test
    fun `signUp allows different logins`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            assertIs<Either.Right<User>>(
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request(login = "user1@example.com")) } },
            )
            assertIs<Either.Right<User>>(
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request(login = "user2@example.com")) } },
            )
        }

    @Test
    fun `signUp создаёт новой организации предустановленные метки посещаемости`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            val result = TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request()) } }
            val user = assertIs<Either.Right<User>>(result).value

            val labels =
                TestPostgres.db
                    .sql("SELECT name, scope::text AS scope FROM attendance_labels WHERE org_id = :orgId ORDER BY position")
                    .bind("orgId", user.orgId)
                    .list { it.asString("name") to it.asString("scope") }

            assertEquals(
                listOf(
                    "Опоздал" to "present",
                    "Ушёл раньше" to "present",
                    "Болеет" to "absent",
                    "Предупредил" to "absent",
                    "Прогул" to "absent",
                ),
                labels,
            )
        }

    /** Регистрирует организацию с логином [login] на языке [lang]. */
    private suspend fun signedUp(login: String = "user@example.com", lang: Lang = Lang.RU): User {
        val result = TestPostgres.db.transaction { context(this, PasswordHasher(), DbUserDisplaySettings()) { signUp(request(login), lang) } }
        return assertIs<Either.Right<User>>(result).value
    }

    /** Название и пояснение источников клиентов организации [orgId]. */
    private suspend fun leadSourcesOf(orgId: OrgId): Set<Pair<String, String>> =
        TestPostgres.db
            .sql("SELECT name, description FROM lead_sources WHERE org_id = :orgId")
            .bind("orgId", orgId)
            .list { it.asString("name") to it.asString("description") }
            .toSet()

    @Test
    fun `signUp на русском создаёт стартовый набор источников клиентов`() =
        runTest {
            val user = signedUp(lang = Lang.RU)

            assertEquals(
                setOf(
                    "Рекомендация" to "Посоветовали друзья, знакомые или действующие клиенты",
                    "Соцсети" to "Публикации и страницы в соцсетях, кроме платной рекламы",
                    "Таргетированная реклама" to "Платная реклама в соцсетях",
                    "Поиск в интернете" to "Поисковые системы, включая контекстную рекламу",
                    "Карты и отзывы" to "Карточка организации на картах и в сервисах отзывов",
                    "Сайт" to "Пришёл напрямую через сайт организации",
                    "Вывеска / проходил мимо" to "Увидел зал, вывеску или листовку поблизости",
                    "Мероприятие" to "Соревнования, открытая тренировка, день открытых дверей, выступление",
                    "Школа / детский сад" to "Презентации и объявления в школах и детских садах",
                    "Другое" to "Всё, что не подходит под остальные источники",
                ),
                leadSourcesOf(user.orgId),
            )
        }

    @Test
    fun `signUp на английском создаёт стартовый набор источников клиентов`() =
        runTest {
            val user = signedUp(lang = Lang.EN)

            assertEquals(
                setOf(
                    "Referral" to "Recommended by friends, acquaintances or current clients",
                    "Social media" to "Posts and pages on social networks, excluding paid ads",
                    "Paid social ads" to "Paid advertising on social networks",
                    "Web search" to "Search engines, including search ads",
                    "Maps & reviews" to "Business listing on maps and review services",
                    "Website" to "Came directly through the organization's website",
                    "Walk-in / signage" to "Saw the venue, a sign or a flyer nearby",
                    "Event" to "Competition, open class, open day or performance",
                    "School / kindergarten" to "Presentations and announcements at schools and kindergartens",
                    "Other" to "Anything that does not fit the other sources",
                ),
                leadSourcesOf(user.orgId),
            )
        }

    @Test
    fun `у каждой организации свой стартовый набор источников`() =
        runTest {
            val first = signedUp(login = "first@example.com")
            val second = signedUp(login = "second@example.com")

            TestPostgres.db
                .sql("DELETE FROM lead_sources WHERE org_id = :orgId AND name = 'Другое'")
                .bind("orgId", first.orgId)
                .execute()

            assertEquals(9, leadSourcesOf(first.orgId).size)
            assertEquals(10, leadSourcesOf(second.orgId).size)
        }
}
