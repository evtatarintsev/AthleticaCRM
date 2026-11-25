import Foundation
import Logging
import PostgresNIO
@testable import ClientsService

/// Контейнер PostgreSQL, общий для всех DB-тестов прогона, — аналог `TestPostgres` Kotlin-сервера.
/// Образ тот же (`postgres:18`), схема — те же миграции Liquibase: файлы `changes/*.sql`
/// применяются по порядку имён, как `includeAll`, директивы Liquibase в них — SQL-комментарии.
/// Тесты не чистят таблицы: каждый заводит свою организацию ([TestOrg]) и может идти параллельно.
enum TestPostgres {
    /// Запуск контейнера и миграций — один раз на процесс.
    private static let started = Task { try await start() }

    /// База данных тестов.
    static func db() async throws -> Database {
        try await started.value
    }

    /// Поднимает контейнер, ждёт готовности, применяет миграции и открывает пул.
    private static func start() async throws -> Database {
        let id = try docker("run", "-d", "--rm", "-e", "POSTGRES_PASSWORD=test", "-p", "127.0.0.1::5432", "postgres:18")
        testContainerId = id
        atexit { removeTestContainer() }
        let address = try docker("port", id, "5432/tcp")
        guard let port = address.split(separator: ":").last.flatMap({ Int($0) }) else {
            throw TestSetupError("Не удалось узнать порт контейнера: \(address)")
        }
        // TCP открывается только у итогового сервера, не у временного сервера инициализации образа.
        while (try? docker("exec", id, "pg_isready", "-q", "-h", "127.0.0.1", "-U", "postgres")) == nil {
            try await Task.sleep(for: .milliseconds(200))
        }
        _ = try docker("exec", "-i", id, "psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", input: try migrations())

        let client = PostgresClient(
            configuration: .init(host: "127.0.0.1", port: port, username: "postgres", password: "test", database: "postgres", tls: .disable)
        )
        Task.detached { await client.run() }
        return Database(client: client, logger: Logger(label: "test-db"))
    }

    /// Текст всех миграций Kotlin-сервера в порядке применения.
    private static func migrations() throws -> Data {
        let dir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("server/src/main/resources/db/changelog/changes")
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path).filter { $0.hasSuffix(".sql") }.sorted()
        var sql = Data()
        for file in files {
            sql.append(try Data(contentsOf: dir.appendingPathComponent(file)))
            sql.append(Data("\n".utf8))
        }
        return sql
    }

    /// Выполняет `docker` с аргументами [args] и входом [input]; вывод без пробелов по краям, код ≠ 0 — ошибка.
    @discardableResult
    static func docker(_ args: String..., input: Data? = nil) throws -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = ["docker"] + args
        let output = Pipe()
        let errors = Pipe()
        process.standardOutput = output
        process.standardError = errors
        let stdin = Pipe()
        if input != nil {
            process.standardInput = stdin
        }
        try process.run()
        if let input {
            stdin.fileHandleForWriting.write(input)
            try stdin.fileHandleForWriting.close()
        }
        let out = output.fileHandleForReading.readDataToEndOfFile()
        let err = errors.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw TestSetupError("docker \(args.joined(separator: " ")): \(String(decoding: err, as: UTF8.self))")
        }
        return String(decoding: out, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Идентификатор контейнера для удаления при выходе процесса.
nonisolated(unsafe) private var testContainerId: String?

/// Удаляет тестовый контейнер; вызывается из `atexit`.
private func removeTestContainer() {
    if let id = testContainerId {
        _ = try? TestPostgres.docker("rm", "-f", id)
    }
}

/// Ошибка подготовки тестового окружения.
struct TestSetupError: Error, CustomStringConvertible {
    /// Описание.
    let description: String
    /// Ошибка с описанием [description].
    init(_ description: String) { self.description = description }
}
