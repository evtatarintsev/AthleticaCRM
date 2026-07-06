package org.athletica.crm.domain.notifications

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/** Тесты доменной сущности уведомлений [DbNotifications] / [DbNotification]. */
class NotificationsTest {
    private val orgId = OrgId.new()
    private val emp1 = EmployeeId.new()
    private val emp2 = EmployeeId.new()
    private val stranger = EmployeeId.new()

    private val notifications = DbNotifications()

    private fun ctx(employeeId: EmployeeId) =
        EmployeeRequestContext(
            lang = Lang.RU,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = employeeId,
            username = "user@example.com",
            clientIp = null,
            permission = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db
                .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId)
                .bind("name", "Org 1")
                .execute()
            listOf(emp1 to "Сотрудник 1", emp2 to "Сотрудник 2", stranger to "Посторонний").forEach { (id, name) ->
                TestPostgres.db
                    .sql("INSERT INTO employees (id, org_id, name, is_owner) VALUES (:id, :orgId, :name, false)")
                    .bind("id", id)
                    .bind("orgId", orgId)
                    .bind("name", name)
                    .execute()
            }
        }
    }

    private suspend fun countNotifications(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM notifications")
            .firstOrNull { row -> row.asLong(0) } ?: 0

    private suspend fun countRecipients(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM notification_recipients")
            .firstOrNull { row -> row.asLong(0) } ?: 0

    @Test
    fun `save создаёт уведомление и записи получателей`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) {
                        notifications.new("Заголовок", "Текст", listOf(emp1, emp2)).save()
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            assertEquals(1, countNotifications())
            assertEquals(2, countRecipients())
        }

    @Test
    fun `of возвращает уведомление получателю и скрывает от постороннего`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) {
                        notifications.new("Заголовок", "Текст", listOf(emp1)).save()
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            val (mine, myUnread) =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            notifications.of(null) to notifications.unreadCount()
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(1, mine.size)
            assertEquals("Заголовок", mine.first().title)
            assertEquals(false, mine.first().isRead)
            assertEquals(1, myUnread)

            val (foreign, foreignUnread) =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(stranger)) {
                            notifications.of(null) to notifications.unreadCount()
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(emptyList(), foreign)
            assertEquals(0, foreignUnread)
        }

    @Test
    fun `markAsRead помечает уведомление получателя и игнорирует чужой контекст`() =
        runTest {
            val id =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            val notification = notifications.new("Заголовок", "Текст", listOf(emp1))
                            notification.save()
                            notification.id
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx(stranger)) {
                        notifications.markAsRead(listOf(id))
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            val stillUnread =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) { notifications.unreadCount() }
                    }
                }.getOrElse { fail("Expected success: $it") }
            assertEquals(1, stillUnread)

            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) { notifications.markAsRead(listOf(id)) }
                }
            }.getOrElse { fail("Expected success: $it") }

            val (unread, read) =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            notifications.of(false) to notifications.of(true)
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }
            assertEquals(emptyList(), unread)
            assertEquals(1, read.size)
        }

    @Test
    fun `markAllAsRead помечает все уведомления сотрудника`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) {
                        notifications.new("A", "1", listOf(emp1)).save()
                        notifications.new("B", "2", listOf(emp1)).save()
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) { notifications.markAllAsRead() }
                }
            }.getOrElse { fail("Expected success: $it") }

            val unread =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) { notifications.unreadCount() }
                    }
                }.getOrElse { fail("Expected success: $it") }
            assertEquals(0, unread)
        }

    @Test
    fun `save с пустым списком получателей ничего не сохраняет`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx(emp1)) {
                        notifications.new("Заголовок", "Текст", emptyList()).save()
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            assertEquals(0, countNotifications())
            assertEquals(0, countRecipients())
        }

    @Test
    fun `save возвращает ошибку если заголовок пустой`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            notifications.new("  ", "Текст", listOf(emp1)).save()
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("NOTIFICATION_TITLE_REQUIRED", result.value.code)
        }

    @Test
    fun `save возвращает ошибку если текст пустой`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            notifications.new("Заголовок", "", listOf(emp1)).save()
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("NOTIFICATION_BODY_REQUIRED", result.value.code)
        }

    @Test
    fun `save возвращает ошибку если заголовок слишком длинный`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx(emp1)) {
                            notifications.new("x".repeat(256), "Текст", listOf(emp1)).save()
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("NOTIFICATION_TITLE_TOO_LONG", result.value.code)
        }
}
