package org.athletica.mobile

import arrow.core.Either
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppTokensResponse
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.session.MemorySessionStore
import org.athletica.mobile.session.ProfileKey
import org.athletica.mobile.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Сетевой слой: Bearer, один refresh на 401, сброс сеанса и ошибки сервера значением. */
class ClientApiTest {
    private val server = FakeServer()
    private val profile = ProfileKey(OrgId.new(), ClientId.new())

    @Test
    fun `истёкший access обновляется одним refresh — запрос повторяется — профиль сохраняется`() =
        runTest {
            val store = MemorySessionStore(Session("a1", "r1", profile))
            server.on("GET client-app/sessions") { if (it.auth == "Bearer a2") ok(ClientAppSessionsResponse(emptyList())) else respond("", HttpStatusCode.Unauthorized) }
            server.json("POST client-app/auth/mobile/refresh") { ClientAppTokensResponse("a2", "r2") }

            val result = server.api(store).sessions()

            assertIs<Either.Right<ClientAppSessionsResponse>>(result)
            assertEquals(Session("a2", "r2", profile), store.session)
            assertEquals(listOf("""{"refreshToken":"r1"}"""), server.callsTo("POST client-app/auth/mobile/refresh").map { it.body })
        }

    @Test
    fun `refresh отклонён — сеанс стёрт и приходит событие выхода`() =
        runTest {
            val store = MemorySessionStore(Session("a1", "r1", profile))
            server.on("GET client-app/sessions") { respond("", HttpStatusCode.Unauthorized) }
            server.on("POST client-app/auth/mobile/refresh") { respond("", HttpStatusCode.Unauthorized) }
            val api = server.api(store)
            val signedOut = async(start = CoroutineStart.UNDISPATCHED) { api.signedOut.first() }

            val result = api.sessions()

            assertEquals(Either.Left(ApiError.Unavailable), result)
            assertNull(store.session)
            signedOut.await()
            assertEquals(1, server.callsTo("POST client-app/auth/mobile/refresh").size)
        }

    @Test
    fun `бизнес-ошибка сервера — значение с кодом и сообщением — сбой сети — Unavailable`() =
        runTest {
            val store = MemorySessionStore(Session("a1", "r1"))
            server.on("POST client-app/connect") { businessError("CLIENT_NOT_FOUND", "Клиент не найден") }
            val api = server.api(store)

            assertEquals(Either.Left(ApiError.Business("CLIENT_NOT_FOUND", "Клиент не найден")), api.connect(ClientAppInvitationRequest(profile.orgId)))
            server.online = false
            assertEquals(Either.Left(ApiError.Unavailable), api.profiles())
            assertTrue(store.session != null)
        }
}
