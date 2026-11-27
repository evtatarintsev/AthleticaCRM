package org.athletica.mobile.session

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId

/**
 * Хранилище сеанса на устройстве: токены и выбранный профиль — единственное, что приложение
 * сохраняет между запусками. Android хранит их зашифрованными ключом из Keystore.
 */
interface SessionStore {
    /** Сохранённый сеанс; `null` — входа не было, был выход или файл не расшифровался. */
    suspend fun load(): Session?

    /** Сохраняет сеанс [session] целиком. */
    suspend fun save(session: Session)

    /** Удаляет сеанс и выбранный профиль. */
    suspend fun clear()
}

/** Сеанс приложения: access токен [accessToken], refresh токен [refreshToken] и выбранный профиль [profile]. */
@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val profile: ProfileKey? = null,
)

/** Выбранный профиль: клиент [clientId] организации [orgId]. */
@Serializable
data class ProfileKey(
    val orgId: OrgId,
    val clientId: ClientId,
)

/** Хранилище сеанса в памяти — для тестов и платформ без защищённого хранилища; [initial] — начальный сеанс. */
class MemorySessionStore(initial: Session? = null) : SessionStore {
    /** Текущий сеанс. */
    var session: Session? = initial
        private set

    override suspend fun load(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}
