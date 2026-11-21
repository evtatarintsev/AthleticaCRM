package org.athletica.crm.read.auth

import org.athletica.crm.api.schemas.auth.DevicePlatform

/** Устройство сеанса, выведенное из User-Agent. */
data class UserAgentInfo(
    /** Браузер с мажорной версией; `null`, если не распознан. */
    val browser: String?,
    /** Операционная система; `null`, если не распознана. */
    val os: String?,
    /** Платформа для иконки. */
    val platform: DevicePlatform,
)

/**
 * Разбирает [userAgent] в браузер и ОС для подписи сеанса. Разбор приблизительный:
 * нераспознанное значение — `null`, а не ошибка.
 */
fun userAgentInfo(userAgent: String?): UserAgentInfo {
    val ua = userAgent.orEmpty()
    val (os, platform) =
        when {
            "iPhone" in ua || "iPad" in ua -> "iOS" to DevicePlatform.IOS
            "Android" in ua -> "Android" to DevicePlatform.ANDROID
            "Windows" in ua -> "Windows" to DevicePlatform.WINDOWS
            "Macintosh" in ua -> "macOS" to DevicePlatform.MACOS
            "Linux" in ua || "CrOS" in ua -> "Linux" to DevicePlatform.LINUX
            else -> null to DevicePlatform.OTHER
        }
    val browser = BROWSERS.firstNotNullOfOrNull { (name, regex) -> regex.find(ua)?.let { "$name ${it.groupValues[1]}" } }
    return UserAgentInfo(browser, os, platform)
}

/** Браузеры в порядке проверки: производные от Chrome раньше Chrome, Chrome раньше Safari. */
private val BROWSERS =
    listOf(
        "Edge" to Regex("""Edg(?:e|A|iOS)?/(\d+)"""),
        "Opera" to Regex("""(?:OPR|Opera)/(\d+)"""),
        "Yandex Browser" to Regex("""YaBrowser/(\d+)"""),
        "Firefox" to Regex("""(?:Firefox|FxiOS)/(\d+)"""),
        "Chrome" to Regex("""(?:Chrome|CriOS)/(\d+)"""),
        "Safari" to Regex("""Version/(\d+)[\d.]* (?:Mobile/\S+ )?Safari/"""),
    )
