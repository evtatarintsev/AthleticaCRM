package org.athletica.crm

import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.clientbalance.AuditClientBalances
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.infra.yookassa.YookassaConfig

/** JWT-конфиг для тестовой среды. */
val testJwtConfig =
    JwtConfig(
        secret = "test-secret-key-for-unit-tests",
        accessTokenTtlMinutes = 15L,
        refreshTokenTtlDays = 30L,
    )

/** Создаёт [Di] с тестовой БД и заглушками инфраструктурных сервисов. */
fun testDi(): Di {
    val audit = PostgresAuditLog()
    return Di(
        databaseConfig = TestPostgres.dbConfig,
        database = TestPostgres.db,
        mailbox = TestMailbox(),
        jwtConfig = testJwtConfig,
        minio = TestMinio.minioService,
        passwordHasher = PasswordHasher(),
        audit = audit,
        orgEmails = DbOrgEmails(),
        emailDispatcher = FakeEmailDispatcher(),
        orgBalances = DbOrgBalances(),
        organizations = DbOrganizations(),
        clientBalances = AuditClientBalances(DbClientBalances(), audit),
        clients = DbClients(),
        branches = DbBranches(),
        customFieldDefinitions = DbCustomFieldDefinitions(),
        yookassaConfig = YookassaConfig(shopId = "test", secretKey = "test", testMode = true, returnUrl = "https://test.example.com/return"),
        payments = DbPayments(),
        paymentGateway =
            object : PaymentGateway {
                override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult = throw UnsupportedOperationException("PaymentGateway not available in tests")
            },
    )
}
