import XCTest
import AthleticaMobile

/// Сеанс в Keychain симулятора: сохранение, очистка и сброс при первом запуске после установки.
/// Тесты Kotlin/Native запускаются без приложения и Keychain не получают, поэтому они здесь.
final class SessionStoreTests: XCTestCase {
    private let service = "org.athletica.mobile.test.\(UUID().uuidString)"
    private let session = Session(accessToken: "access", refreshToken: "refresh", profile: nil)

    override func tearDown() async throws {
        try await IosSessionStore(service: service).clear()
        UserDefaults.standard.removeObject(forKey: "\(service).installed")
    }

    func testSavedSessionIsReadByNewStore() async throws {
        try await IosSessionStore(service: service).save(session: session)

        let loaded = try await IosSessionStore(service: service).load()
        XCTAssertEqual(loaded, session)
    }

    func testClearedStoreHasNoSession() async throws {
        let store = IosSessionStore(service: service)
        try await store.save(session: session)

        try await store.clear()

        let loaded = try await store.load()
        XCTAssertNil(loaded)
    }

    func testFirstLaunchAfterReinstallDropsKeychainSession() async throws {
        try await IosSessionStore(service: service).save(session: session)
        UserDefaults.standard.removeObject(forKey: "\(service).installed")

        let loaded = try await IosSessionStore(service: service).load()
        XCTAssertNil(loaded)
    }
}
