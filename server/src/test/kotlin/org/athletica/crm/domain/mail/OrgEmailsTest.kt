package org.athletica.crm.domain.mail

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.EmailEmployees
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.employeeContext
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asLong
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OrgEmailsTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    @Test
    fun `system mail and existing employee invitations use their own organization`() =
        runTest {
            orThrow {
                val first = OrgId.new()
                val second = OrgId.new()
                val emails = DbOrgEmails()
                TestPostgres.db.transaction {
                    for (id in listOf(first, second)) {
                        sql("INSERT INTO organizations (id, name) VALUES (:id, 'Mail test')").bind("id", id).execute()
                    }
                    context(SystemContexts.forOrg(first)) {
                        emails.schedule(listOf(EmailAddress("owner@example.com")), "Welcome", "Text", "<p>Text</p>")
                    }
                    val employees = EmailEmployees(DbEmployees(DbUsers(PasswordHasher()), DbRoles()), emails)
                    context(employeeContext(second)) {
                        val employee = employees.new(EmployeeId.new(), testName("Trainer"), null, null, null, EmployeePermissionSelection(), EmployeeBranchAccess.All)
                        employee.invite(EmailAddress("trainer@example.com"), "invitePassword")
                    }
                    val pending = emails.pending()
                    assertEquals(2, pending.size)
                    assertEquals(listOf(EmailAddress("owner@example.com")), pending.single { it.orgId == first }.to)
                    val invitation = pending.single { it.orgId == second }
                    assertEquals(listOf(EmailAddress("trainer@example.com")), invitation.to)
                    assertContains(invitation.textBody, "invitePassword")
                    assertContains(invitation.htmlBody, "Trainer")
                    assertEquals(0L, sql("SELECT count(*) AS n FROM user_email_verifications").firstOrNull { it.asLong("n") })
                }
            }
        }
}
