package org.athletica.crm

import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.minio.MinioClient
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.channels.AuditChannelIntegrations
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.clientbalance.AuditClientBalances
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.clientnotes.ClientNotes
import org.athletica.crm.domain.clientnotes.DbClientNotes
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.discipline.AuditDisciplines
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.domain.employees.AuditEmployees
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.EmailEmployees
import org.athletica.crm.domain.enrollments.AuditEnrollments
import org.athletica.crm.domain.enrollments.DbEnrollments
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.events.DomainEventWorker
import org.athletica.crm.domain.events.handlers.GroupCreatedHandler
import org.athletica.crm.domain.events.handlers.GroupScheduleChangedHandler
import org.athletica.crm.domain.groups.AuditGroups
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.hall.AuditHalls
import org.athletica.crm.domain.hall.DbHalls
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.leadSource.AuditLeadSources
import org.athletica.crm.domain.leadSource.DbLeadSources
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.domain.mail.DbEmailDispatcher
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.domain.mail.Mailbox
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.domain.messagedelivery.ChannelRegistry
import org.athletica.crm.domain.messagedelivery.DbDeliveries
import org.athletica.crm.domain.messagedelivery.MessageDispatcher
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.org.LocMemCachedOrganizations
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.orgbalance.LocMemCachedOrgBalances
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.domain.sessions.AuditSessions
import org.athletica.crm.domain.sessions.DbSessions
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.domain.settings.UserDisplaySettings
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.integrations.messaging.StubChannelRegistry
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.infra.mail.SmtpConfig
import org.athletica.infra.mail.SmtpMailbox
import org.athletica.infra.yookassa.YookassaApi
import org.athletica.infra.yookassa.YookassaConfig
import org.athletica.infra.yookassa.YookassaPaymentGateway
import org.athletica.infra.yookassa.createYookassaHttpClient
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
    val clientBalances: ClientBalances,
    val clients: Clients,
    val clientNotes: ClientNotes = DbClientNotes(),
    val branches: Branches,
    val customFieldDefinitions: CustomFieldDefinitions,
    val userDisplaySettings: UserDisplaySettings = DbUserDisplaySettings(),
    val tasks: Tasks = DbTasks(),
    val yookassaConfig: YookassaConfig,
    val payments: Payments,
    val paymentGateway: PaymentGateway,
) {
    val users = DbUsers(passwordHasher)
    val roles = DbRoles()
    val employees =
        AuditEmployees(
            EmailEmployees(DbEmployees(users, roles), orgEmails),
            audit,
        )
    val disciplines: Disciplines = AuditDisciplines(DbDisciplines(), audit)
    val leadSources: LeadSources = AuditLeadSources(DbLeadSources(), audit)
    val bus: DomainEventBus = DomainEventBus()
    val groups: Groups = AuditGroups(DbGroups(bus), audit)
    val enrollments: Enrollments = AuditEnrollments(DbEnrollments(), audit)
    val sessions: Sessions = AuditSessions(DbSessions(), audit)
    val halls: Halls = AuditHalls(DbHalls(), audit)
    val eventWorker: DomainEventWorker = DomainEventWorker(database, bus)
    val channelIntegrations: ChannelIntegrations = AuditChannelIntegrations(DbChannelIntegrations(), audit)
    val clientContacts = DbClientContacts()
    val conversations = DbConversations()
    val deliveries = DbDeliveries()
    val channelRegistry: ChannelRegistry = StubChannelRegistry()
    val messageDispatcher: MessageDispatcher =
        MessageDispatcher(database, deliveries, channelIntegrations, channelRegistry)

    init {
        bus.register(GroupCreatedHandler(database, groups, sessions, employees))
        bus.register(GroupScheduleChangedHandler(database, groups, sessions, employees))
    }
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
    val ykConfig = yookassaConfig()
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
        clientBalances = AuditClientBalances(DbClientBalances(), audit),
        clients = DbClients(),
        branches = DbBranches(),
        customFieldDefinitions = DbCustomFieldDefinitions(),
        userDisplaySettings = DbUserDisplaySettings(),
        yookassaConfig = ykConfig,
        payments = DbPayments(),
        paymentGateway = YookassaPaymentGateway(YookassaApi(createYookassaHttpClient(CIO), ykConfig)),
    )
}

/** Создаёт [YookassaConfig] из конфигурации приложения. */
fun Application.yookassaConfig() =
    YookassaConfig(
        shopId = environment.config.property("yookassa.shopId").getString(),
        secretKey = environment.config.property("yookassa.secretKey").getString(),
        testMode = environment.config.property("yookassa.testMode").getString().toBoolean(),
        returnUrl = environment.config.property("yookassa.returnUrl").getString(),
    )

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

fun Application.minio(): MinioService {
    val accessKey = environment.config.property("minio.accessKey").getString()
    val secretKey = environment.config.property("minio.secretKey").getString()
    val bucket = environment.config.property("minio.bucket").getString()
    return MinioService(
        internalClient =
            MinioClient
                .builder()
                .endpoint(environment.config.property("minio.endpoint").getString())
                .credentials(accessKey, secretKey)
                .build(),
        publicClient =
            MinioClient
                .builder()
                .endpoint(environment.config.property("minio.publicEndpoint").getString())
                .credentials(accessKey, secretKey)
                .build(),
        bucket = bucket,
    ).also { it.ensureBucketExists() }
}

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
