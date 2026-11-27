package org.athletica.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.mobile.session.ProfileKey
import org.athletica.mobile.session.Session
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Хранилище сеанса на устройстве: Keystore, шифрование файла и очистка. */
@RunWith(AndroidJUnit4::class)
class AndroidSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AndroidSessionStore.of(context)

    @Test
    fun сеанс_сохраняется_зашифрованным_и_стирается() =
        runTest {
            val session = Session("access-token", "refresh-token", ProfileKey(OrgId.new(), ClientId.new()))

            store.save(session)
            assertEquals(session, store.load())
            val file = File(context.filesDir, "datastore/session.bin")
            assertFalse(file.readBytes().decodeToString().contains("refresh-token"))

            store.clear()
            assertNull(store.load())
        }
}
