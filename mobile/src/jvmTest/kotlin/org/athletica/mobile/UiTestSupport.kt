package org.athletica.mobile

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceEntrySchema
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipState
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleSessionSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitsResponse
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.mobile.ui.AppLinks
import java.util.Locale

/** Документы для тестов. */
val LINKS = AppLinks("https://example.com/terms", "https://example.com/privacy")

/** Тексты экранов в тестах — русские, как у пользователей. */
fun russian() {
    Locale.setDefault(Locale.forLanguageTag("ru-RU"))
}

/** Ждёт на экране текст [text] (подстрокой, если [substring]). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitText(text: String, substring: Boolean = false) {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
}

/** Ждёт текст [text] и нажимает на него. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.click(text: String) {
    awaitText(text)
    onNodeWithText(text).performClick()
}

/** Иван Петров в «Динамо». */
val IVAN = ClientAppProfileSchema(OrgId.new(), "Динамо", ClientId.new(), "Иван Петров")

/** Мария в «Волне». */
val MARIA = ClientAppProfileSchema(OrgId.new(), "Волна", ClientId.new(), "Мария Петрова")

/** Запланированное занятие Ивана: среда 18:00 в зале «Большой». */
val SESSION =
    ClientAppScheduleSessionSchema(
        sessionId = SessionId.new(),
        date = LocalDate(2026, 11, 25),
        startTime = LocalTime(18, 0),
        endTime = LocalTime(19, 0),
        groupName = "Футбол 2015",
        hallName = "Большой",
        coaches = listOf("Сергей Иванов"),
        status = SessionStatus.SCHEDULED,
        movedFrom = null,
        hasStarted = false,
        absenceNoticeAt = null,
    )

/** Абонемент «8 занятий»: списано 5, до 30 ноября. */
val MEMBERSHIP =
    ClientAppMembershipSchema(
        id = MembershipId.new(),
        name = "8 занятий",
        state = ClientAppMembershipState.ACTIVE,
        sessionsTotal = 8,
        sessionsRemaining = 3,
        startDate = LocalDate(2026, 11, 1),
        endDate = LocalDate(2026, 11, 30),
        activateOn = null,
        endingSoon = emptyList(),
        ended = false,
        freeze = null,
        closedOn = null,
        debits = emptyList(),
    )

/**
 * Данные клиента на сервере: профили [profiles] с [newInvitations] приглашениями, расписание
 * [sessions] и абонементы [memberships] (пусто — абонемента нет); баланс −1 000 ₽ с начислением
 * +500 ₽ и двумя неоплаченными занятиями, одно пропущенное посещение.
 */
fun FakeServer.clientData(
    profiles: List<ClientAppProfileSchema> = listOf(IVAN),
    newInvitations: Int = 0,
    sessions: () -> List<ClientAppScheduleSessionSchema> = { listOf(SESSION) },
    memberships: List<ClientAppMembershipSchema> = listOf(MEMBERSHIP),
) {
    json("GET client-app/profiles") { ClientAppProfilesResponse(profiles, newInvitations) }
    json("GET client-app/home") { call -> profiles.first { it.clientId.value.toString() == call.query["clientId"] }.let { ClientAppHomeResponse(it.clientName, it.orgName) } }
    json("GET client-app/schedule") { sessions().let { ClientAppScheduleResponse(LocalDate(2026, 11, 23), LocalDate(2026, 11, 25), it, it.firstOrNull()) } }
    json("GET client-app/memberships") { ClientAppMembershipsResponse(memberships) }
    json("GET client-app/balance") {
        ClientAppBalanceResponse(
            Money(-100_000, Currency.RUB),
            2,
            listOf(ClientAppBalanceEntrySchema(LedgerTransactionId.new(), LocalDate(2026, 11, 20), LedgerKind.ADJUSTMENT, Money(50_000, Currency.RUB))),
        )
    }
    json("GET client-app/visits") {
        ClientAppVisitsResponse(LocalDate(2026, 11, 1), listOf(ClientAppVisitSchema(SessionId.new(), LocalDate(2026, 11, 18), LocalTime(18, 0), "Футбол 2015", AttendancePresence.ABSENT)), 0)
    }
}
