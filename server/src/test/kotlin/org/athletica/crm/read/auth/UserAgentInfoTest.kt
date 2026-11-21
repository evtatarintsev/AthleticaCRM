package org.athletica.crm.read.auth

import org.athletica.crm.api.schemas.auth.DevicePlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты разбора User-Agent в подпись сеанса. */
class UserAgentInfoTest {
    @Test
    fun `распознаёт браузеры и платформы`() {
        val cases =
            listOf(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36" to
                    UserAgentInfo("Chrome 142", "macOS", DevicePlatform.MACOS),
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0" to
                    UserAgentInfo("Edge 142", "Windows", DevicePlatform.WINDOWS),
                "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0" to
                    UserAgentInfo("Firefox 133", "Linux", DevicePlatform.LINUX),
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1" to
                    UserAgentInfo("Safari 18", "iOS", DevicePlatform.IOS),
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/142.0 Mobile/15E148 Safari/604.1" to
                    UserAgentInfo("Chrome 142", "iOS", DevicePlatform.IOS),
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 YaBrowser/25.2.0.0 Mobile Safari/537.36" to
                    UserAgentInfo("Yandex Browser 25", "Android", DevicePlatform.ANDROID),
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 OPR/117.0.0.0" to
                    UserAgentInfo("Opera 117", "Windows", DevicePlatform.WINDOWS),
            )
        cases.forEach { (ua, expected) -> assertEquals(expected, userAgentInfo(ua), ua) }
    }

    @Test
    fun `пустой и неизвестный User-Agent не распознаются`() {
        val unknown = UserAgentInfo(null, null, DevicePlatform.OTHER)
        assertEquals(unknown, userAgentInfo(null))
        assertEquals(unknown, userAgentInfo(""))
        assertEquals(unknown, userAgentInfo("curl/8.7.1"))
    }
}
