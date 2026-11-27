package org.athletica.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppRequestCodeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppTokensResponse
import org.athletica.mobile.session.MemorySessionStore
import org.athletica.mobile.session.Session
import org.athletica.mobile.ui.App
import org.athletica.mobile.ui.key
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Сценарии приложения на фейковом сервере: вход, подключение, разделы, выход. */
@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private val server = FakeServer()

    @BeforeTest
    fun setUp() {
        russian()
    }

    @Test
    fun `вход по коду с принятием условий открывает главную и сохраняет сеанс`() =
        runComposeUiTest {
            val store = MemorySessionStore()
            server.json("POST client-app/auth/request-code") { ClientAppRequestCodeResponse(true, 60, "v1") }
            server.on("POST client-app/auth/mobile/login") {
                if ("\"acceptedTermsVersion\":\"v1\"" in it.body) {
                    ok(ClientAppTokensResponse("a1", "r1"))
                } else {
                    businessError("CLIENT_APP_TERMS_REQUIRED", "Примите условия")
                }
            }
            server.clientData()
            setContent { App(server.api(store), LINKS) }

            awaitText("Вход")
            onNode(hasSetTextAction()).performTextInput("+7 900 000-00-00")
            click("Получить код")
            awaitText("Код отправлен в Telegram или по SMS на номер +7 900 000-00-00")
            awaitText("Новый код можно запросить через 1:00")
            onNode(hasSetTextAction()).performTextInput("123456")
            click("Войти")
            click("Принимаю условия и вхожу")

            awaitText("Это кабинет клиента Иван Петров")
            assertEquals(Session("a1", "r1"), store.session)
        }

    @Test
    fun `код не пришёл в Telegram — повтор по SMS с новым отсчётом`() =
        runComposeUiTest {
            var requests = 0
            server.json("POST client-app/auth/request-code") { ClientAppRequestCodeResponse(true, if (requests++ == 0) 0 else 60, "v1") }
            setContent { App(server.api(MemorySessionStore()), LINKS) }

            awaitText("Вход")
            onNode(hasSetTextAction()).performTextInput("+79000000000")
            click("Получить код")
            click("Отправить по SMS")

            awaitText("Код отправлен по SMS на номер +79000000000")
            awaitText("Новый код можно запросить через 1:00")
            assertTrue(server.callsTo("POST client-app/auth/request-code").last().body.contains("\"viaSms\":true"))
        }

    @Test
    fun `без сети — «Нет связи с сервером» и повтор`() =
        runComposeUiTest {
            server.clientData()
            server.online = false
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Нет связи с сервером")
            server.online = true
            click("Повторить")

            awaitText("Это кабинет клиента Иван Петров")
        }

    @Test
    fun `нижнее меню из четырёх разделов`() =
        runComposeUiTest {
            server.clientData()
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Это кабинет клиента Иван Петров")
            listOf("Главная", "Чат", "Ещё").forEach { onNodeWithText(it).assertExists() }
            onAllNodesWithText("Расписание").fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
            click("Ещё")
            awaitText("Устройства")
        }

    @Test
    fun `приглашение без профилей — «Подключить» открывает главную`() =
        runComposeUiTest {
            var connected = false
            server.clientData()
            server.json("GET client-app/profiles") {
                ClientAppProfilesResponse(if (connected) listOf(IVAN) else emptyList(), if (connected) 0 else 1)
            }
            server.json("GET client-app/invitations") {
                ClientAppInvitationsResponse(if (connected) emptyList() else listOf(ClientAppInvitationSchema(IVAN.orgId, "Динамо", Clock.System.now(), 1)))
            }
            server.on("POST client-app/connect") {
                connected = true
                ok(Unit)
            }
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Вас пригласили")
            awaitText("1 клиент с вашим номером")
            click("Подключить")

            awaitText("Это кабинет клиента Иван Петров")
            assertTrue(server.callsTo("POST client-app/connect").single().body.contains(IVAN.orgId.value.toString()))
        }

    @Test
    fun `«Это не я» сообщает серверу и убирает приглашение`() =
        runComposeUiTest {
            var answered = false
            server.json("GET client-app/profiles") { ClientAppProfilesResponse(emptyList(), if (answered) 0 else 1) }
            server.json("GET client-app/invitations") {
                ClientAppInvitationsResponse(if (answered) emptyList() else listOf(ClientAppInvitationSchema(IVAN.orgId, "Динамо", Clock.System.now(), 2)))
            }
            server.on("POST client-app/not-me") {
                answered = true
                ok(Unit)
            }
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            click("Это не я")

            awaitText("Профилей пока нет — спросите у администратора")
            assertEquals(1, server.callsTo("POST client-app/not-me").size)
        }

    @Test
    fun `переключение профиля показывает его главную и запоминается`() =
        runComposeUiTest {
            val store = MemorySessionStore(Session("a1", "r1"))
            server.clientData(profiles = listOf(IVAN, MARIA), newInvitations = 1)
            setContent { App(server.api(store), LINKS) }

            click("Сменить профиль")
            awaitText("Новые приглашения: 1")
            click("Мария Петрова")

            awaitText("Это кабинет клиента Мария Петрова")
            waitUntil(timeoutMillis = 5_000) { store.session?.profile == MARIA.key }
        }

    @Test
    fun `нет абонемента — нет блока абонемента, остальные блоки на месте`() =
        runComposeUiTest {
            server.clientData(memberships = emptyList())
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Ближайшее занятие")
            awaitText("Баланс")
            onAllNodesWithText("Абонемент").assertCountEquals(0)
            onAllNodesWithText("Осталось", substring = true).assertCountEquals(0)
        }

    @Test
    fun `«Не придём» в расписании — занятие помечено и предупреждение можно отменить`() =
        runComposeUiTest {
            var notified = false
            server.clientData(sessions = { listOf(if (notified) SESSION.copy(absenceNoticeAt = Clock.System.now()) else SESSION) })
            server.on("POST client-app/absence-notices/create") {
                notified = true
                ok(Unit)
            }
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Это кабинет клиента Иван Петров")
            onAllNodesWithText("Расписание")[0].performClick()
            click("Не придём")

            awaitText("Вы предупредили, что не придёте")
            awaitText("Отменить предупреждение")
            assertTrue(server.callsTo("POST client-app/absence-notices/create").single().body.contains(SESSION.sessionId.value.toString()))
        }

    @Test
    fun `абонементы, баланс и посещения — как в примерах client-app-overview`() =
        runComposeUiTest {
            server.clientData()
            setContent { App(server.api(MemorySessionStore(Session("a1", "r1"))), LINKS) }

            awaitText("Осталось 3 из 8, до 30.11.2026")
            awaitText("Неоплаченных занятий: 2")
            click("История баланса")
            awaitText("Чтобы оплатить, обратитесь к администратору")
            awaitText("20.11.2026 Начисление")
            awaitText("+500,00 ₽")
            click("Ещё")
            click("Посещения")
            awaitText("Не был")
        }

    @Test
    fun `после выхода повторный запуск открывает вход`() =
        runComposeUiTest {
            val store = MemorySessionStore(Session("a1", "r1", MARIA.key))
            server.clientData()
            server.on("POST client-app/auth/logout") { ok(Unit) }
            var launch by mutableStateOf(0)
            setContent { key(launch) { App(server.api(store), LINKS) } }

            click("Ещё")
            click("Выйти")
            awaitText("Получить код")
            assertNull(store.session)
            assertEquals("Bearer a1", server.callsTo("POST client-app/auth/logout").single().auth)

            launch++
            awaitText("Получить код")
        }

    @Test
    fun `сеанс завершён с другого устройства — экран входа`() =
        runComposeUiTest {
            val store = MemorySessionStore(Session("a1", "r1"))
            server.clientData()
            server.on("GET client-app/profiles") { respond("", HttpStatusCode.Unauthorized) }
            server.on("POST client-app/auth/mobile/refresh") { respond("", HttpStatusCode.Unauthorized) }
            setContent { App(server.api(store), LINKS) }

            awaitText("Получить код")
            assertNull(store.session)
        }
}
