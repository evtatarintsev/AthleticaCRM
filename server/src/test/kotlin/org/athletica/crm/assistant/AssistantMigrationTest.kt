package org.athletica.crm.assistant

import kotlinx.coroutines.runBlocking
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.testDi
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** Проверяет миграции и изоляцию составных внешних ключей PostgreSQL. */
class AssistantMigrationTest {
    /** База отклоняет связь чужой организации, сообщения и карточки и вторую ожидающую карточку диалога. */
    @Test
    fun foreignKeysKeepOrganizationAndDialogTogether(): Unit =
        orThrow {
            runBlocking {
                TestPostgres.truncate()
                val di = testDi()
                val owner = assistantOwner(di)
                val other = assistantOwner(di)
                val pg = TestPostgres.container
                DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
                    connection.createStatement().use { sql ->
                        assertEquals(
                            7,
                            sql.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'assistant_%'").let {
                                it.next()
                                it.getInt(1)
                            },
                        )
                        assertFailsWith<SQLException> {
                            sql.executeUpdate("INSERT INTO assistant_dialogs(id,org_id,employee_id) VALUES ('${Uuid.random()}','${owner.orgId}','${other.employee.id}')")
                        }
                        val dialog = Uuid.random()
                        val otherDialog = Uuid.random()
                        sql.executeUpdate("INSERT INTO assistant_dialogs(id,org_id,employee_id) VALUES ('$dialog','${owner.orgId}','${owner.employee.id}'),('$otherDialog','${other.orgId}','${other.employee.id}')")
                        assertFailsWith<SQLException> {
                            sql.executeUpdate("INSERT INTO assistant_messages(id,dialog_id,org_id,role,status,deadline) VALUES ('${Uuid.random()}','$dialog','${other.orgId}','assistant','completed',now())")
                        }
                        val message = Uuid.random()
                        sql.executeUpdate("INSERT INTO assistant_messages(id,dialog_id,org_id,role,status,deadline) VALUES ('$message','$dialog','${owner.orgId}','assistant','completed',now())")
                        assertFailsWith<SQLException> {
                            sql.executeUpdate("INSERT INTO assistant_client_drafts(client_id,dialog_id,org_id,message_id,payload,status) VALUES ('${Uuid.random()}','$otherDialog','${other.orgId}','$message','{}','pending')")
                        }
                        val source = Uuid.random()
                        sql.executeUpdate("INSERT INTO assistant_import_sources(id,dialog_id,org_id,message_id,kind,data) VALUES ('$source','$dialog','${owner.orgId}','$message','table','{}')")
                        sql.executeUpdate("INSERT INTO assistant_client_drafts(client_id,dialog_id,org_id,message_id,payload,status) VALUES ('${Uuid.random()}','$dialog','${owner.orgId}','$message','{}','pending')")
                        val pendingImport = "INSERT INTO assistant_import_drafts(id,dialog_id,org_id,message_id,source_id,payload,status) VALUES ('${Uuid.random()}','$dialog','${owner.orgId}','$message','$source','{}','pending')"
                        assertFailsWith<SQLException> { sql.executeUpdate(pendingImport) }
                        sql.executeUpdate("UPDATE assistant_client_drafts SET status = 'superseded' WHERE dialog_id = '$dialog'")
                        sql.executeUpdate(pendingImport)
                    }
                }
            }
        }
}
