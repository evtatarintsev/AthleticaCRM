package org.athletica.crm

/** Тип платформы, на которой запущено приложение. */
enum class PlatformType {
    WEB,
    DESKTOP,
    ANDROID,
    IOS,
}

/** Возвращает тип текущей платформы. */
expect val currentPlatform: PlatformType
