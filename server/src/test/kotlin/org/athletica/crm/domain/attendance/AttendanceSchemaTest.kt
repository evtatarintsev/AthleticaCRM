package org.athletica.crm.domain.attendance

import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Проверки ограничений схемы журнала посещаемости на уровне БД. */
class AttendanceSchemaTest {
    private val fixture = AttendanceFixture()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun insertRow(sessionId: SessionId, clientId: ClientId) =
        TestPostgres.db
            .sql(
                """
                INSERT INTO session_attendance (session_id, client_id, kind, presence)
                VALUES (:s, :c, 'regular', 'absent') RETURNING id
                """.trimIndent(),
            )
            .bind("s", sessionId)
            .bind("c", clientId)
            .firstOrNull { it.asUuid("id") }!!

    @Test
    fun `повторная строка журнала для той же пары занятие и клиент отклоняется`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession()
            val client = fixture.insertClient("Аня")
            insertRow(session, client)

            assertFailsWith<R2dbcDataIntegrityViolationException> { insertRow(session, client) }
        }

    @Test
    fun `удаление строки журнала удаляет её связи с метками`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession()
            val client = fixture.insertClient("Аня")
            val label = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val row = insertRow(session, client)
            TestPostgres.db
                .sql("INSERT INTO session_attendance_labels (attendance_id, label_id) VALUES (:a, :l)")
                .bind("a", row)
                .bind("l", label)
                .execute()

            TestPostgres.db.sql("DELETE FROM session_attendance WHERE id = :id").bind("id", row).execute()

            val links =
                TestPostgres.db
                    .sql("SELECT COUNT(*) AS cnt FROM session_attendance_labels WHERE attendance_id = :a")
                    .bind("a", row)
                    .firstOrNull { it.asLong("cnt") }
            assertEquals(0L, links)
        }
}
