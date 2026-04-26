package org.athletica.crm

import io.ktor.server.application.Application
import io.minio.MinioClient
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.clientbalance.AuditClientBalances
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.discipline.AuditDisciplines
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.domain.employees.AuditEmployees
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.EmailEmployees
import org.athletica.crm.domain.employees.EmployeePermissions
import org.athletica.crm.domain.enrollments.AuditEnrollments
import org.athletica.crm.domain.enrollments.DbEnrollments
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.domain.groups.AuditGroups
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.mail.DbEmailDispatcher
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.domain.mail.Mailbox
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.org.LocMemCachedOrganizations
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.orgbalance.LocMemCachedOrgBalances
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.infra.mail.SmtpConfig
import org.athletica.infra.mail.SmtpMailbox
import kotlin.time.Duration.Companion.seconds

data class Di(
    val databaseConfig: DatabaseConfig,
    val database: Database,
    val mailbox: Mailbox,
    val minio: MinioService,
    val passwordHasher: PasswordHasher,
    val audit: AuditLog,
    val jwtConfig: JwtConfig,
    val orgEmails: OrgEmails,
    val emailDispatcher: EmailDispatcher,
    val orgBalances: OrgBalances,
    val organizations: Organizations,
    val employeePermissions: EmployeePermissions,
    val clientBalances: ClientBalances,
    val clients: Clients,
) {
    val users = DbUsers(passwordHasher)
    val roles = DbRoles()
    val employees =
        AuditEmployees(
            EmailEmployees(DbEmployees(users, roles), orgEmails),
            audit,
        )
    val disciplines: Disciplines = AuditDisciplines(DbDisciplines(), audit)
    val groups: Groups = AuditGroups(DbGroups(), audit)
    val enrollments: Enrollments = AuditEnrollments(DbEnrollments(), audit)
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)

fun Application.di(): Di {
    val dbConfig = databaseConfig()
    val db = createDatabase(dbConfig)
    val mb = mailbox()
    val passwordHasher = PasswordHasher()
    val orgEmails = DbOrgEmails()
    val audit = PostgresAuditLog()
    return Di(
        dbConfig,
        db,
        mb,
        minio(),
        passwordHasher,
        audit,
        jwtConfig(),
        orgEmails,
        emailDispatcher = DbEmailDispatcher(db, orgEmails, mb, checkEvery = 10.seconds),
        orgBalances = LocMemCachedOrgBalances(DbOrgBalances()),
        organizations = LocMemCachedOrganizations(DbOrganizations()),
        employeePermissions = EmployeePermissions(),
        clientBalances = AuditClientBalances(DbClientBalances(), audit),
        clients = DbClients(),
    )
}

fun Application.databaseConfig() =
    DatabaseConfig(
        environment.config.property("database.url").getString(),
        environment.config.property("database.user").getString(),
        environment.config.property("database.password").getString(),
    )

/**
 * Создаёт [Mailbox] на основе SMTP-настроек из конфигурации.
 */
fun Application.mailbox(): Mailbox =
    SmtpMailbox(
        SmtpConfig(
            host = environment.config.property("smtp.host").getString(),
            port = environment.config.property("smtp.port").getString().toInt(),
            username = environment.config.property("smtp.username").getString(),
            password = environment.config.property("smtp.password").getString(),
            fromAddress = environment.config.property("smtp.fromAddress").getString(),
            fromName = environment.config.property("smtp.fromName").getString(),
        ),
    )

fun Application.minio() =
    MinioService(
        client =
            MinioClient
                .builder()
                .endpoint(environment.config.property("minio.endpoint").getString())
                .credentials(
                    environment.config.property("minio.accessKey").getString(),
                    environment.config.property("minio.secretKey").getString(),
                ).build(),
        bucket = environment.config.property("minio.bucket").getString(),
    ).also { it.ensureBucketExists() }

/**
 * Создаёт [Database] с R2DBC пулом соединений.
 * JDBC URL автоматически преобразуется в R2DBC URL.
 * [jdbcUrl] — JDBC URL вида `jdbc:postgresql://host:port/db`,
 * [user] и [password] — учётные данные пользователя БД.
 */
fun createDatabase(
    jdbcUrl: String,
    user: String,
    password: String,
): Database {
    val r2dbcUrl = jdbcUrl.replace("jdbc:postgresql", "r2dbc:postgresql")
    val options =
        ConnectionFactoryOptions
            .parse(r2dbcUrl)
            .mutate()
            .option(ConnectionFactoryOptions.USER, user)
            .option(ConnectionFactoryOptions.PASSWORD, password)
            .build()
    val pool =
        ConnectionPool(
            ConnectionPoolConfiguration
                .builder(ConnectionFactories.get(options))
                .initialSize(2)
                .maxSize(10)
                .build(),
        )
    return Database(pool)
}

fun createDatabase(dbConfig: DatabaseConfig) = createDatabase(dbConfig.url, dbConfig.user, dbConfig.password)

fun Application.jwtConfig() =
    JwtConfig(
        secret = environment.config.property("jwt.secret").getString(),
        accessTokenTtlMinutes = environment.config.property("jwt.accessTokenTtlMinutes").getString().toLong(),
        refreshTokenTtlDays = environment.config.property("jwt.refreshTokenTtlDays").getString().toLong(),
    )
