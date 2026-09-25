package org.athletica.crm.contracts

import io.minio.MinioClient
import org.athletica.crm.DatabaseConfig
import org.athletica.crm.Di
import org.athletica.crm.createDatabase
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.domain.mail.Mailbox
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.MinioService
import org.athletica.infra.mail.Email
import org.athletica.infra.yookassa.YookassaConfig

/** Адрес, по которому гарантированно никто не слушает: к инфраструктуре генератор не обращается. */
private const val UNREACHABLE_HOST = "127.0.0.1:9"

/**
 * [Di] для построения дерева маршрутов без инфраструктуры.
 *
 * Пул соединений R2DBC и клиенты MinIO создаются лениво и при регистрации маршрутов
 * не подключаются, поэтому генерации контрактов не нужны ни БД, ни объектное хранилище.
 * Внешние сервисы, которые могли бы сработать, заменены отказывающими заглушками.
 */
fun offlineDi(): Di {
    val databaseConfig = DatabaseConfig("jdbc:postgresql://$UNREACHABLE_HOST/contracts", "contracts", "contracts")
    val minioClient =
        MinioClient
            .builder()
            .endpoint("http://$UNREACHABLE_HOST")
            .credentials("contracts", "contracts")
            .build()
    val audit = PostgresAuditLog()
    return Di(
        databaseConfig = databaseConfig,
        database = createDatabase(databaseConfig),
        mailbox = OfflineMailbox,
        minio = MinioService(internalClient = minioClient, publicClient = minioClient, bucket = "contracts"),
        passwordHasher = PasswordHasher(),
        audit = audit,
        jwtConfig = JwtConfig(secret = "contracts", accessTokenTtlMinutes = 1L, refreshTokenTtlDays = 1L),
        orgEmails = DbOrgEmails(),
        emailDispatcher = OfflineEmailDispatcher,
        orgBalances = DbOrgBalances(),
        organizations = DbOrganizations(),
        clientBalances = DbClientBalances(),
        clients = DbClients(),
        branches = DbBranches(),
        customFieldDefinitions = DbCustomFieldDefinitions(),
        yookassaConfig = YookassaConfig(shopId = "contracts", secretKey = "contracts", testMode = true, returnUrl = "http://$UNREACHABLE_HOST"),
        payments = DbPayments(),
        paymentGateway = OfflinePaymentGateway,
    )
}

/** Почтовый ящик, который отказывается отправлять письма. */
private object OfflineMailbox : Mailbox {
    override suspend fun send(email: Email): Unit = throw UnsupportedOperationException("Почта недоступна при генерации контрактов")
}

/** Диспетчер писем, которому нечего отправлять. */
private object OfflineEmailDispatcher : EmailDispatcher {
    override suspend fun dispatchPending() = Unit
}

/** Платёжный шлюз, который отказывается создавать платежи. */
private object OfflinePaymentGateway : PaymentGateway {
    override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult = throw UnsupportedOperationException("Платёжный шлюз недоступен при генерации контрактов")
}
