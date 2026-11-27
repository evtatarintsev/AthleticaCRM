package org.athletica.mobile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import org.athletica.crm.api.appJson
import org.athletica.mobile.session.Session
import org.athletica.mobile.session.SessionStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Сеанс в DataStore-файле, зашифрованном AES-256-GCM (Tink); ключ шифрования защищён ключом
 * Android Keystore и не покидает устройство. Файл исключён из резервных копий манифестом,
 * а чужой или испорченный файл просто не расшифруется — это «нет сеанса».
 */
class AndroidSessionStore private constructor(context: Context) : SessionStore {
    private val store: DataStore<Session?> =
        DataStoreFactory.create(EncryptedSessionSerializer(aead(context))) { File(context.filesDir, "datastore/session.bin") }

    override suspend fun load(): Session? = store.data.first()

    override suspend fun save(session: Session) {
        store.updateData { session }
    }

    override suspend fun clear() {
        store.updateData { null }
    }

    companion object {
        @Volatile
        private var instance: AndroidSessionStore? = null

        /** Единственное хранилище процесса: DataStore не допускает двух экземпляров на один файл. */
        fun of(context: Context): AndroidSessionStore = instance ?: synchronized(this) { instance ?: AndroidSessionStore(context.applicationContext).also { instance = it } }

        /** AEAD-примитив Tink с ключом, зашифрованным мастер-ключом Android Keystore. */
        private fun aead(context: Context): Aead {
            AeadConfig.register()
            return AndroidKeysetManager
                .Builder()
                .withSharedPref(context, "session_keyset", "athletica_session_keyset")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://athletica_session_master_key")
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }
}

/** Сериализатор сеанса: JSON, зашифрованный [aead]; связан с именем файла через associated data. */
private class EncryptedSessionSerializer(private val aead: Aead) : Serializer<Session?> {
    override val defaultValue: Session? = null

    override suspend fun readFrom(input: InputStream): Session? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            appJson.decodeFromString<Session>(aead.decrypt(bytes, AD).decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeTo(t: Session?, output: OutputStream) {
        if (t != null) {
            output.write(aead.encrypt(appJson.encodeToString(t).encodeToByteArray(), AD))
        }
    }

    private companion object {
        /** Associated data шифра: шифротекст другого файла сюда не подставить. */
        val AD = "athletica-session".encodeToByteArray()
    }
}
