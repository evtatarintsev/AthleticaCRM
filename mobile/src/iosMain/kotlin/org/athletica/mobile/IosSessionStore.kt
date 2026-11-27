package org.athletica.mobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.SerializationException
import org.athletica.crm.api.appJson
import org.athletica.mobile.session.Session
import org.athletica.mobile.session.SessionStore
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Сеанс в одной записи Keychain с доступом `AfterFirstUnlockThisDeviceOnly`: запись не уходит в
 * iCloud-связку и не восстанавливается из резервной копии на другом iPhone, но читается в фоне
 * после первой разблокировки. Keychain переживает удаление приложения, поэтому первый запуск после
 * установки (флаг в `NSUserDefaults`, который удаление стирает) удаляет прежнюю запись.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSessionStore(
    private val service: String = "org.athletica.mobile.session",
) : SessionStore {
    init {
        val defaults = NSUserDefaults.standardUserDefaults
        val installedKey = "$service.installed"
        if (!defaults.boolForKey(installedKey)) {
            delete()
            defaults.setBool(true, installedKey)
        }
    }

    override suspend fun load(): Session? {
        val json = read() ?: return null
        return try {
            appJson.decodeFromString<Session>(json)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(session: Session) {
        delete()
        val data = NSString.create(string = appJson.encodeToString(session)).dataUsingEncoding(NSUTF8StringEncoding)
        val value = CFBridgingRetain(data)
        val status =
            try {
                query(kSecValueData to value, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) { SecItemAdd(it, null) }
            } finally {
                CFRelease(value)
            }
        check(status == errSecSuccess) { "Keychain: сеанс не сохранён, статус $status" }
    }

    override suspend fun clear() {
        delete()
    }

    /** Удаляет запись сеанса; отсутствие записи — не ошибка. */
    private fun delete() {
        val status = query { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain: сеанс не удалён, статус $status" }
    }

    /** JSON сеанса из записи; `null` — записи нет. */
    private fun read(): String? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = query(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) {
                return null
            }
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            NSString.create(data, NSUTF8StringEncoding)?.toString()
        }

    /**
     * Вызывает [block] с запросом к записи сеанса (класс, сервис, учётная запись) и полями [extra].
     * Словарь удерживает [extra] сам; освобождать их — забота вызывающего.
     */
    private inline fun <T> query(
        vararg extra: Pair<CFTypeRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val owned = listOf(CFBridgingRetain(service), CFBridgingRetain(ACCOUNT))
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, owned[0])
        CFDictionaryAddValue(dict, kSecAttrAccount, owned[1])
        extra.forEach { CFDictionaryAddValue(dict, it.first, it.second) }
        try {
            return block(dict)
        } finally {
            CFRelease(dict)
            owned.forEach { CFRelease(it) }
        }
    }

    private companion object {
        /** Учётная запись Keychain: запись сеанса одна. */
        const val ACCOUNT = "session"
    }
}
