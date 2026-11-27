import SwiftUI
import AthleticaMobile

/// Оболочка iOS-приложения: весь интерфейс — общие Compose-экраны модуля `:mobile`.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            // Отступы safe area и клавиатуры считает Compose (`safeDrawingPadding`), а не SwiftUI.
            ComposeView().ignoresSafeArea()
        }
    }
}

/// Контроллер общих экранов; адреса API и документов — из Info.plist (настройки сборки `ATHLETICA_*`).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppKt.MainViewController(
            apiUrl: setting("AthleticaApiUrl"),
            termsUrl: setting("AthleticaTermsUrl"),
            privacyUrl: setting("AthleticaPrivacyUrl")
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    private func setting(_ key: String) -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            fatalError("Info.plist: нет \(key)")
        }
        return value
    }
}
