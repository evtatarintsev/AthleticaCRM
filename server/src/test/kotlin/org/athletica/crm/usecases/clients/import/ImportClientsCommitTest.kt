package org.athletica.crm.usecases.clients.import

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestMinio
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse
import org.athletica.crm.api.schemas.clients.import.ColumnMapping
import org.athletica.crm.api.schemas.clients.import.ImportTarget
import org.athletica.crm.api.schemas.clients.import.LeadSourceAction
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.leadSource.DbLeadSources
import org.athletica.crm.storage.asDouble
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class ImportClientsCommitTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private data class TestOrg(val orgId: Uuid, val userId: Uuid, val employeeId: Uuid)

    private suspend fun setupOrg(): TestOrg {
        val orgId = Uuid.generateV7()
        val userId = Uuid.generateV7()
        val employeeId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", "Test Org")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId)
            .bind("login", "import-user-$userId@example.com")
            .bind("hash", "x")
            .execute()
        TestPostgres.db
            .sql(
                """
                INSERT INTO employees (id, user_id, org_id, name, is_owner)
                VALUES (:id, :userId, :orgId, :name, true)
                """.trimIndent(),
            ).bind("id", employeeId)
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("name", "Test Owner")
            .execute()
        return TestOrg(orgId, userId, employeeId)
    }

    private suspend fun uploadCsv(
        org: TestOrg,
        content: String,
        filename: String = "clients.csv",
    ): UploadId {
        val uploadId = UploadId.new()
        val objectKey = "${org.orgId}/$uploadId/$filename"
        val bytes = content.toByteArray(Charsets.UTF_8)
        TestMinio.minioService.uploadObject(objectKey, bytes.inputStream(), bytes.size.toLong(), "text/csv")
        TestPostgres.db
            .sql(
                """
                INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
                VALUES (:id, :orgId, :uploadedBy, :objectKey, :originalName, :contentType, :sizeBytes)
                """.trimIndent(),
            ).bind("id", uploadId)
            .bind("orgId", org.orgId)
            .bind("uploadedBy", org.userId)
            .bind("objectKey", objectKey)
            .bind("originalName", filename)
            .bind("contentType", "text/csv")
            .bind("sizeBytes", bytes.size.toLong())
            .execute()
        return uploadId
    }

    private fun ctx(org: TestOrg) =
        RequestContext(
            lang = Lang.EN,
            userId = UserId(org.userId),
            orgId = OrgId(org.orgId),
            branchId = BranchId.new(),
            employeeId = EmployeeId(org.employeeId),
            username = "user@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )

    private suspend fun runImport(
        org: TestOrg,
        request: ClientImportCommitRequest,
    ): Either<DomainError, ClientImportCommitResponse> =
        either {
            context(TestPostgres.db, TestMinio.minioService, ctx(org)) {
                importClientsCommit(
                    request = request,
                    clients = DbClients(),
                    balances = DbClientBalances(),
                    leadSources = DbLeadSources(),
                    definitions = DbCustomFieldDefinitions(),
                ).bind()
            }
        }

    @Test
    fun `dryRun не пишет клиентов в БД`() =
        runTest {
            val org = setupOrg()
            val uploadId =
                uploadCsv(
                    org,
                    """
                    ФИО;Дата рождения
                    Иванов Иван;15.11.2019
                    Петров Пётр;
                    """.trimIndent(),
                )
            val response =
                runImport(
                    org = org,
                    request =
                        ClientImportCommitRequest(
                            uploadId = uploadId,
                            columnMapping =
                                listOf(
                                    ColumnMapping("ФИО", ImportTarget.Name),
                                    ColumnMapping("Дата рождения", ImportTarget.Birthday),
                                ),
                            defaultGender = Gender.MALE,
                            dryRun = true,
                        ),
                )
            val result = assertIs<Either.Right<ClientImportCommitResponse>>(response).value
            assertEquals(2, result.totalRows)
            assertEquals(2, result.imported)
            assertEquals(0, result.skipped)

            val count =
                TestPostgres.db
                    .sql("SELECT COUNT(*)::text as c FROM clients WHERE org_id = :orgId")
                    .bind("orgId", org.orgId)
                    .firstOrNull { it.asString("c") }
            assertEquals("0", count)
        }

    @Test
    fun `создаёт клиентов и баланс по admin_credit, отрицательный → admin_debit`() =
        runTest {
            val org = setupOrg()
            val uploadId =
                uploadCsv(
                    org,
                    """
                    ФИО;Баланс
                    Иванов;3056.00
                    Петров;-266.00
                    Сидоров;
                    """.trimIndent(),
                )
            val response =
                runImport(
                    org = org,
                    request =
                        ClientImportCommitRequest(
                            uploadId = uploadId,
                            columnMapping =
                                listOf(
                                    ColumnMapping("ФИО", ImportTarget.Name),
                                    ColumnMapping("Баланс", ImportTarget.Balance),
                                ),
                            defaultGender = Gender.MALE,
                            dryRun = false,
                        ),
                )
            val result = assertIs<Either.Right<ClientImportCommitResponse>>(response).value
            assertEquals(3, result.imported)

            val ivanovBalance =
                TestPostgres.db
                    .sql(
                        """
                        SELECT j.amount, j.operation_type
                        FROM client_balance_journal j
                        JOIN clients c ON c.id = j.client_id
                        WHERE c.name = :name AND c.org_id = :orgId
                        """.trimIndent(),
                    ).bind("name", "Иванов")
                    .bind("orgId", org.orgId)
                    .firstOrNull { row ->
                        row.asDouble("amount") to row.asString("operation_type")
                    }
            assertEquals(3056.0 to "admin_credit", ivanovBalance)

            val petrovBalance =
                TestPostgres.db
                    .sql(
                        """
                        SELECT j.amount, j.operation_type
                        FROM client_balance_journal j
                        JOIN clients c ON c.id = j.client_id
                        WHERE c.name = :name AND c.org_id = :orgId
                        """.trimIndent(),
                    ).bind("name", "Петров")
                    .bind("orgId", org.orgId)
                    .firstOrNull { row ->
                        row.asDouble("amount") to row.asString("operation_type")
                    }
            assertEquals(-266.0 to "admin_debit", petrovBalance)

            val sidorovJournalCount =
                TestPostgres.db
                    .sql(
                        """
                        SELECT COUNT(*)::text as c
                        FROM client_balance_journal j
                        JOIN clients c ON c.id = j.client_id
                        WHERE c.name = :name
                        """.trimIndent(),
                    ).bind("name", "Сидоров")
                    .firstOrNull { it.asString("c") }
            assertEquals("0", sidorovJournalCount)
        }

    @Test
    fun `строка без имени попадает в ERROR, остальные импортируются`() =
        runTest {
            val org = setupOrg()
            val uploadId =
                uploadCsv(
                    org,
                    """
                    ФИО

                    Хороший Клиент
                    """.trimIndent() + "\n",
                )
            val response =
                runImport(
                    org = org,
                    request =
                        ClientImportCommitRequest(
                            uploadId = uploadId,
                            columnMapping = listOf(ColumnMapping("ФИО", ImportTarget.Name)),
                            defaultGender = Gender.MALE,
                            dryRun = false,
                        ),
                )
            val result = assertIs<Either.Right<ClientImportCommitResponse>>(response).value
            assertEquals(1, result.imported)
        }

    @Test
    fun `создаёт новые LeadSource через CreateNew и привязывает клиентов`() =
        runTest {
            val org = setupOrg()
            val uploadId =
                uploadCsv(
                    org,
                    """
                    ФИО;Источник
                    Иванов;Инстаграм
                    Петров;Инстаграм
                    """.trimIndent(),
                )
            val response =
                runImport(
                    org = org,
                    request =
                        ClientImportCommitRequest(
                            uploadId = uploadId,
                            columnMapping =
                                listOf(
                                    ColumnMapping("ФИО", ImportTarget.Name),
                                    ColumnMapping("Источник", ImportTarget.LeadSource),
                                ),
                            defaultGender = Gender.MALE,
                            leadSourceMapping = mapOf("Инстаграм" to LeadSourceAction.CreateNew("Инстаграм")),
                            dryRun = false,
                        ),
                )
            val result = assertIs<Either.Right<ClientImportCommitResponse>>(response).value
            assertEquals(2, result.imported)
            assertEquals(1, result.createdLeadSources.size)
            assertEquals("Инстаграм", result.createdLeadSources[0].name)

            val sourceCount =
                TestPostgres.db
                    .sql(
                        """
                        SELECT COUNT(*)::text as c
                        FROM clients
                        WHERE org_id = :orgId AND lead_source_id IS NOT NULL
                        """.trimIndent(),
                    ).bind("orgId", org.orgId)
                    .firstOrNull { it.asString("c") }
            assertEquals("2", sourceCount)
        }
}
