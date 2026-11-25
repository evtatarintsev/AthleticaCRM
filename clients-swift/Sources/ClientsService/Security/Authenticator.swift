import Foundation
import JWTKit
import Vapor

/// Claims access-токена, выпущенного Kotlin-сервером (`JwtConfig.makeAccessToken`).
struct AccessTokenPayload: JWTPayload {
    /// Издатель.
    let iss: IssuerClaim
    /// Аудитория.
    let aud: AudienceClaim
    /// Срок действия.
    let exp: ExpirationClaim
    /// Пользователь.
    let userId: String
    /// Организация.
    let orgId: String
    /// Филиал.
    let branchId: String
    /// Сотрудник.
    let employeeId: String
    /// Имя пользователя.
    let username: String
    /// Сессия входа.
    let sid: String
    /// Тип токена: `access` или `refresh`.
    let type: String

    func verify(using algorithm: some JWTAlgorithm) async throws {
        try exp.verifyNotExpired()
        guard iss.value == "athletica-crm", aud.value.contains("athletica-crm-users"), type == "access" else {
            throw JWTError.claimVerificationFailure(failedClaim: iss, reason: "Чужой или не access-токен")
        }
    }
}

/// Собирает [EmployeeRequestContext] из JWT (заголовок `Authorization: Bearer` или cookie
/// `access_token`) и БД — порт `contextFromRequest` из `RouteWithContext.kt`: заблокированный
/// сотрудник или завершённая сессия — `unauthorized`, активность сотрудника отмечается в [presence].
struct Authenticator: Sendable {
    /// Ключи проверки подписи HS256.
    let keys: JWTKeyCollection
    /// База данных.
    let db: Database
    /// Отметки активности сотрудников.
    let presence: EmployeePresence

    /// Контекст сотрудника, выполняющего запрос [req].
    func context(_ req: Request) async throws(AppError) -> EmployeeRequestContext {
        let token = req.headers.bearerAuthorization?.token ?? req.cookies["access_token"]?.string
        guard let token else {
            throw .unauthorized
        }
        let payload: AccessTokenPayload
        do {
            payload = try await keys.verify(token, as: AccessTokenPayload.self)
        } catch {
            throw .unauthorized
        }
        guard let userId = UUID(uuidString: payload.userId).map(UserId.init),
              let orgId = UUID(uuidString: payload.orgId).map(OrgId.init),
              let branchId = UUID(uuidString: payload.branchId).map(BranchId.init),
              let employeeId = UUID(uuidString: payload.employeeId).map(EmployeeId.init),
              let sessionId = UUID(uuidString: payload.sid).map(AuthSessionId.init)
        else {
            throw .unauthorized
        }
        let presence = presence
        let currency = try await db.transaction { tr throws(AppError) in
            let blocked = try await tr.first(
                "SELECT blocked_at IS NOT NULL FROM employees WHERE id = \(employeeId.value) AND org_id = \(orgId.value)"
            ) { row in try row.decode(Bool.self) }
            guard blocked == false else {
                throw AppError.unauthorized
            }
            let session = try await tr.first(
                "SELECT 1 FROM auth_sessions WHERE id = \(sessionId.value) AND user_id = \(userId.value)"
            ) { _ in true }
            guard session != nil else {
                throw AppError.unauthorized
            }
            try await presence.touch(employeeId, tr: tr)
            let code = try await tr.first("SELECT trim(currency) FROM organizations WHERE id = \(orgId.value)") { row in
                try row.decode(String.self)
            }
            guard let currency = code.flatMap({ Currency(rawValue: $0.uppercased()) }) else {
                throw AppError.infrastructure("Организация \(orgId) не найдена")
            }
            return currency
        }
        return EmployeeRequestContext(
            userId: userId,
            orgId: orgId,
            branchId: branchId,
            employeeId: employeeId,
            username: payload.username,
            lang: Lang.from(acceptLanguage: req.headers.first(name: .acceptLanguage)),
            clientIp: req.headers.first(name: "X-Forwarded-For")?.split(separator: ",").first
                .map { $0.trimmingCharacters(in: .whitespaces) } ?? req.remoteAddress?.ipAddress,
            currency: currency
        )
    }
}

/// Отметки последней активности сотрудников (`employees.last_seen_at`), не чаще раза в минуту на сотрудника.
actor EmployeePresence {
    /// Минимальный интервал между записями одного сотрудника.
    private let throttle: TimeInterval = 60
    /// Момент последней записи по сотруднику в этом процессе.
    private var lastWritten: [EmployeeId: Date] = [:]

    /// Отмечает активность сотрудника [employeeId] в транзакции [tr].
    func touch(_ employeeId: EmployeeId, tr: Transaction) async throws(AppError) {
        let now = Date()
        if let previous = lastWritten[employeeId], previous > now - throttle {
            return
        }
        try await tr.execute("UPDATE employees SET last_seen_at = \(now) WHERE id = \(employeeId.value)")
        lastWritten[employeeId] = now
    }
}
