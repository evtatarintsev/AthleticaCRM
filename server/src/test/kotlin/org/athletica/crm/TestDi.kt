package org.athletica.crm

import arrow.core.raise.context.Raise
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.PaymentGatewayError
import org.athletica.crm.security.ClientAppJwtConfig
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.finance.ClientPayments
import org.athletica.infra.yookassa.YookassaConfig

/** Ключ подписи токенов в тестах — общий для CRM и кабинета, как в проде: контуры различает аудитория. */
private const val TEST_JWT_SECRET = "test-secret-key-for-unit-tests"

/** JWT-конфиг для тестовой среды. */
val testJwtConfig =
    JwtConfig(
        secret = TEST_JWT_SECRET,
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
        clientAppJwtConfig = ClientAppJwtConfig(TEST_JWT_SECRET, accessTokenTtlMinutes = 15L, refreshTokenTtlDays = 60L),
        minio = TestMinio.minioService,
        passwordHasher = PasswordHasher(),
        audit = audit,
        orgEmails = DbOrgEmails(),
        emailDispatcher = FakeEmailDispatcher(),
        orgBalances = DbOrgBalances(),
        organizations = DbOrganizations(),
        clientPayments = ClientPayments(audit = audit),
        clients = DbClients(),
        branches = DbBranches(),
        customFieldDefinitions = DbCustomFieldDefinitions(),
        yookassaConfig = YookassaConfig(shopId = "test", secretKey = "test", testMode = true, returnUrl = "https://test.example.com/return"),
        payments = DbPayments(),
        paymentGateway =
            object : PaymentGateway {
                context(raise: Raise<PaymentGatewayError>)
                override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult = throw UnsupportedOperationException("PaymentGateway not available in tests")
            },
    )
}
