import Foundation
import Vapor

/// Пустой ответ команды: HTTP 200 без тела, как `Unit` у Kotlin-сервера.
struct Empty: Encodable, Sendable {}

/// Файл в ответе: тело как есть с заголовками `Content-Type` и `Content-Disposition`.
struct FileResponse: Encodable, Sendable {
    /// Содержимое файла.
    let body: Data
    /// MIME-тип.
    let contentType: String
    /// Имя файла для скачивания.
    let fileName: String

    func encode(to encoder: Encoder) throws {
        throw EncodingError.invalidValue(self, .init(codingPath: [], debugDescription: "Файл отдаётся телом ответа, а не JSON"))
    }
}

/// Регистратор маршрутов с контекстом сотрудника — порт `RouteWithContext.kt`.
/// Тело маршрута получает декодированный запрос и [EmployeeRequestContext] и бросает только [AppError];
/// ответ на ошибку формируется здесь: бизнес-ошибка — 400 с `ErrorResponse`, нет сессии — 401, прочее — 500.
struct RouteWithContext {
    /// Группа маршрутов `/api`.
    let routes: any RoutesBuilder
    /// Сборщик контекста сотрудника.
    let auth: Authenticator

    /// POST-маршрут [path] с JSON-телом [Req] и JSON-ответом [Res].
    func post<Req: Decodable & Sendable, Res: Encodable & Sendable>(
        _ path: PathComponent...,
        body: @escaping @Sendable (Req, EmployeeRequestContext) async throws(AppError) -> Res
    ) {
        let auth = auth
        routes.on(.POST, path) { req async -> Response in
            await Self.handle(req, auth, "Invalid request body", parse: { try req.content.decode(Req.self, using: JSONDecoder()) }, run: body)
        }
    }

    /// POST-маршрут [path], телу которого нужен и сам HTTP-запрос [Request] (например, query-параметры).
    func post<Req: Decodable & Sendable, Res: Encodable & Sendable>(
        _ path: PathComponent...,
        withRequest body: @escaping @Sendable (Req, EmployeeRequestContext, Request) async throws(AppError) -> Res
    ) {
        let auth = auth
        routes.on(.POST, path) { req async -> Response in
            await Self.handle(req, auth, "Invalid request body", parse: { try req.content.decode(Req.self, using: JSONDecoder()) }) {
                (request, ctx) throws(AppError) in try await body(request, ctx, req)
            }
        }
    }

    /// GET-маршрут [path] с параметрами [Req] из query-строки и JSON-ответом [Res].
    func get<Req: Decodable & Sendable, Res: Encodable & Sendable>(
        _ path: PathComponent...,
        body: @escaping @Sendable (Req, EmployeeRequestContext) async throws(AppError) -> Res
    ) {
        let auth = auth
        routes.on(.GET, path) { req async -> Response in
            await Self.handle(req, auth, "Invalid query parameters", parse: { try req.query.decode(Req.self) }, run: body)
        }
    }

    /// Проверяет сессию, декодирует запрос [parse] (ошибка — `INVALID_REQUEST` с [invalidMessage]),
    /// выполняет [run] и переводит итог в HTTP-ответ.
    private static func handle<Req, Res: Encodable>(
        _ req: Request,
        _ auth: Authenticator,
        _ invalidMessage: String,
        parse: () throws -> Req,
        run: (Req, EmployeeRequestContext) async throws(AppError) -> Res
    ) async -> Response {
        do {
            let ctx = try await auth.context(req)
            let result = try await run(try decode(invalidMessage, parse), ctx)
            if result is Empty {
                return Response(status: .ok)
            }
            if let file = result as? FileResponse {
                var headers = HTTPHeaders()
                headers.replaceOrAdd(name: .contentType, value: file.contentType)
                headers.replaceOrAdd(name: .contentDisposition, value: "attachment; filename=\"\(file.fileName)\"")
                return Response(status: .ok, headers: headers, body: .init(data: file.body))
            }
            return json(.ok, result)
        } catch {
            return respondError(error, req)
        }
    }

    /// Результат [parse]; ошибка формата — `INVALID_REQUEST` с сообщением [message], как у Kotlin-сервера.
    private static func decode<T>(_ message: String, _ parse: () throws -> T) throws(AppError) -> T {
        do {
            return try parse()
        } catch {
            throw .domain("INVALID_REQUEST", message)
        }
    }

    /// Ответ на ошибку [error].
    private static func respondError(_ error: AppError, _ req: Request) -> Response {
        switch error {
        case let .domain(domain):
            return json(.badRequest, ErrorResponse(code: domain.code, message: domain.message))
        case .unauthorized:
            return Response(status: .unauthorized, body: .init(string: "Token expired or invalid"))
        case let .constraintViolation(detail), let .infrastructure(detail):
            req.logger.error("Необработанная ошибка: \(detail)")
            return Response(status: .internalServerError)
        }
    }

    /// JSON-ответ со статусом [status] и телом [value].
    private static func json(_ status: HTTPResponseStatus, _ value: some Encodable) -> Response {
        do {
            var headers = HTTPHeaders()
            headers.contentType = .json
            return Response(status: status, headers: headers, body: .init(data: try apiJson.encode(value)))
        } catch {
            return Response(status: .internalServerError)
        }
    }
}

/// Кодировщик ответов API: моменты времени — ISO 8601 с долями секунды в UTC, как `kotlin.time.Instant`.
let apiJson: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .custom { date, encoder in
        var container = encoder.singleValueContainer()
        try container.encode(date.formatted(.iso8601.year().month().day().time(includingFractionalSeconds: true).timeZone(separator: .omitted)))
    }
    return encoder
}()
