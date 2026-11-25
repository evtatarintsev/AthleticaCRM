import JWTKit
import Logging
import PostgresNIO
import Vapor

/// Точка входа сервиса клиентов: читает окружение, собирает зависимости и запускает HTTP-сервер.
/// Схему БД ведёт Kotlin-сервер (Liquibase); этот сервис только читает и пишет таблицы.
@main
enum App {
    /// Запускает сервис.
    static func main() async throws {
        var env = try Environment.detect()
        try LoggingSystem.bootstrap(from: &env)
        let app = try await Application.make(env)
        let config = try Config.fromEnvironment()
        app.http.server.configuration.hostname = "0.0.0.0"
        app.http.server.configuration.port = config.port

        let pg = PostgresClient(
            configuration: .init(host: config.dbHost, port: config.dbPort, username: config.dbUser, password: config.dbPassword, database: config.dbName, tls: .disable),
            backgroundLogger: app.logger
        )
        let keys = JWTKeyCollection()
        await keys.add(hmac: HMACKey(from: config.jwtSecret), digestAlgorithm: .sha256)

        let db = Database(client: pg, logger: app.logger)
        let audit = PostgresAuditLog()
        let definitions = DbCustomFieldDefinitions()
        let enrollments = AuditEnrollments(delegate: DbEnrollments(), audit: audit)
        let leaveGroups = LeaveGroupsOnArchive(db: db, enrollments: enrollments)
        let bus = DomainEventBus()
            .on(ClientArchived.self) { orgId, event throws(AppError) in try await leaveGroups.clientArchived(orgId, event) }
        let deps = ClientsDeps(
            db: db,
            clients: AuditClients(delegate: DbClients(definitions: definitions, events: bus), audit: audit),
            contacts: DbClientContacts(),
            enrollments: enrollments,
            clientList: DbClientListView(),
            clientBalance: DbClientBalanceView(),
            unpaidSessions: DbUnpaidSessionsView(),
            definitions: definitions
        )
        app.get("health") { _ in "ok" }
        let routes = RouteWithContext(routes: app.grouped("api"), auth: Authenticator(keys: keys, db: db, presence: EmployeePresence()))
        routes.clientsRoutes(deps)
        routes.clientNotesRoutes(db: db, notes: DbClientNotes())
        let worker = DomainEventWorker(db: db, bus: bus, logger: Logger(label: "domain-events"))

        // Фоновые задачи живут в той же группе, что и HTTP-сервер: остановка сервера отменяет их.
        try await withThrowingDiscardingTaskGroup { group in
            group.addTask { await pg.run() }
            group.addTask { await worker.run() }
            try await app.execute()
            group.cancelAll()
        }
        try await app.asyncShutdown()
    }
}

/// Настройки сервиса из переменных окружения; имена совпадают с Kotlin-сервером, где это возможно.
struct Config {
    /// Порт HTTP-сервера.
    let port: Int
    /// Хост PostgreSQL.
    let dbHost: String
    /// Порт PostgreSQL.
    let dbPort: Int
    /// Имя базы данных.
    let dbName: String
    /// Пользователь PostgreSQL.
    let dbUser: String
    /// Пароль PostgreSQL.
    let dbPassword: String
    /// Секрет HMAC для проверки JWT — общий с Kotlin-сервером.
    let jwtSecret: String

    /// Настройки из окружения; обязательная переменная не задана — ошибка запуска.
    static func fromEnvironment() throws -> Config {
        func required(_ name: String) throws -> String {
            guard let value = Environment.get(name), !value.isEmpty else {
                throw Abort(.internalServerError, reason: "Не задана переменная окружения \(name)")
            }
            return value
        }
        return Config(
            port: Environment.get("PORT").flatMap(Int.init) ?? 8080,
            dbHost: Environment.get("POSTGRES_HOST") ?? "localhost",
            dbPort: Environment.get("POSTGRES_PORT").flatMap(Int.init) ?? 5432,
            dbName: Environment.get("POSTGRES_DB") ?? "athletica",
            dbUser: try required("POSTGRES_USER"),
            dbPassword: try required("POSTGRES_PASSWORD"),
            jwtSecret: try required("JWT_SECRET")
        )
    }
}
