package org.athletica.mobile.api

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppAbsenceNoticeRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppLoginRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppRefreshRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppRequestCodeRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppRequestCodeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppTerminateSessionRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppTokensResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitsResponse
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.mobile.session.ProfileKey
import org.athletica.mobile.session.Session
import org.athletica.mobile.session.SessionStore

/** Ошибка обращения к клиентскому API — значение, а не исключение. */
sealed interface ApiError {
    /** Бизнес-ошибка сервера (HTTP 400) с кодом [code] и уже переведённым сообщением [message]. */
    data class Business(val code: String, val message: String) : ApiError

    /** Нет связи с сервером или сервер ответил неожиданно — экран предлагает повторить. */
    data object Unavailable : ApiError
}

/**
 * Клиентское API организаций (`/api/client-app/...`) по адресу [baseUrl] через движок [engine].
 * Токены хранит [store]: access уходит в `Authorization: Bearer`, на `401` делается один
 * `mobile/refresh`, а если сеанс обновить нельзя — сеанс стирается и приходит событие [signedOut].
 * [userAgent] называет устройство в списке сеансов (`Athletica (Android 14)` → «Android»).
 */
class ClientApi(
    engine: HttpClientEngine,
    baseUrl: String,
    private val store: SessionStore,
    userAgent: String,
) {
    private val signedOutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Сеанс завершён сервером (выход на другом устройстве, удаление аккаунта) — нужен экран входа. */
    val signedOut: SharedFlow<Unit> = signedOutEvents

    private val http =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(appJson) }
            install(UserAgent) { agent = userAgent }
            install(Auth) {
                bearer {
                    loadTokens { store.load()?.let { BearerTokens(it.accessToken, it.refreshToken) } }
                    sendWithoutRequest { true }
                    refreshTokens {
                        val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                        val response =
                            client.post("client-app/auth/mobile/refresh") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(ClientAppRefreshRequest(refresh))
                            }
                        when {
                            response.status.isSuccess() -> response.body<ClientAppTokensResponse>().let { saveTokens(it) }
                            response.status == HttpStatusCode.Unauthorized -> {
                                endSession()
                                signedOutEvents.tryEmit(Unit)
                                null
                            }
                            else -> null
                        }
                    }
                }
            }
            defaultRequest { url(baseUrl) }
        }

    /** Есть ли на устройстве сеанс. */
    suspend fun hasSession(): Boolean = store.load() != null

    /** Выбранный на устройстве профиль. */
    suspend fun storedProfile(): ProfileKey? = store.load()?.profile

    /** Запоминает выбор профиля [profile] на устройстве. */
    suspend fun storeProfile(profile: ProfileKey) {
        store.load()?.let { store.save(it.copy(profile = profile)) }
    }

    /** Запрос кода на номер [phone]; [viaSms] — по SMS вместо Telegram. */
    suspend fun requestCode(phone: String, viaSms: Boolean): Either<ApiError, ClientAppRequestCodeResponse> = call { post("client-app/auth/request-code") { json(ClientAppRequestCodeRequest(phone, viaSms)) } }

    /** Вход номером [phone] и кодом [code]; [acceptedTermsVersion] — принятая версия условий. Сохраняет сеанс. */
    suspend fun login(phone: String, code: String, acceptedTermsVersion: String?): Either<ApiError, Unit> =
        call<ClientAppTokensResponse> { post("client-app/auth/mobile/login") { json(ClientAppLoginRequest(phone, code, acceptedTermsVersion)) } }
            .map { saveTokens(it) }

    /** Выход: сервер завершает сеанс, устройство стирает сеанс и профиль даже без связи. */
    suspend fun logout() {
        call<Unit> { post("client-app/auth/logout") }
        endSession()
    }

    /** Удаление аккаунта; при успехе устройство стирает сеанс и профиль. */
    suspend fun deleteAccount(): Either<ApiError, Unit> = call<Unit> { post("client-app/account/delete") }.onRight { endSession() }

    /** Сеансы аккаунта. */
    suspend fun sessions(): Either<ApiError, ClientAppSessionsResponse> = call { get("client-app/sessions") }

    /** Завершает сеанс [sessionId] другого устройства. */
    suspend fun terminate(sessionId: ClientAppSessionId): Either<ApiError, Unit> = call { post("client-app/sessions/terminate") { json(ClientAppTerminateSessionRequest(sessionId)) } }

    /** Подключённые профили и число новых приглашений. */
    suspend fun profiles(): Either<ApiError, ClientAppProfilesResponse> = call { get("client-app/profiles") }

    /** Неподключённые приглашения. */
    suspend fun invitations(): Either<ApiError, ClientAppInvitationsResponse> = call { get("client-app/invitations") }

    /** «Подключить» приглашение организации [request]. */
    suspend fun connect(request: ClientAppInvitationRequest): Either<ApiError, Unit> = call { post("client-app/connect") { json(request) } }

    /** «Это не я» на приглашение организации [request]. */
    suspend fun notMe(request: ClientAppInvitationRequest): Either<ApiError, Unit> = call { post("client-app/not-me") { json(request) } }

    /** Главная профиля [profile]. */
    suspend fun home(profile: ProfileKey): Either<ApiError, ClientAppHomeResponse> = call { get("client-app/home") { profile(profile) } }

    /** Расписание профиля [profile] на неделю с [weekOf]; `null` — текущая неделя. */
    suspend fun schedule(profile: ProfileKey, weekOf: LocalDate?): Either<ApiError, ClientAppScheduleResponse> =
        call {
            get("client-app/schedule") {
                profile(profile)
                weekOf?.let { parameter("weekOf", it.toString()) }
            }
        }

    /** Посещения профиля [profile] за месяц с [monthOf]; `null` — текущий месяц. */
    suspend fun visits(profile: ProfileKey, monthOf: LocalDate?): Either<ApiError, ClientAppVisitsResponse> =
        call {
            get("client-app/visits") {
                profile(profile)
                monthOf?.let { parameter("monthOf", it.toString()) }
            }
        }

    /** Абонементы профиля [profile]. */
    suspend fun memberships(profile: ProfileKey): Either<ApiError, ClientAppMembershipsResponse> = call { get("client-app/memberships") { profile(profile) } }

    /** Баланс профиля [profile]. */
    suspend fun balance(profile: ProfileKey): Either<ApiError, ClientAppBalanceResponse> = call { get("client-app/balance") { profile(profile) } }

    /** «Не придём» профиля [profile] на занятие [sessionId]; [cancel] — отменить предупреждение. */
    suspend fun absenceNotice(profile: ProfileKey, sessionId: SessionId, cancel: Boolean): Either<ApiError, Unit> =
        call {
            val action = if (cancel) "cancel" else "create"
            post("client-app/absence-notices/$action") { json(ClientAppAbsenceNoticeRequest(profile.orgId, profile.clientId, sessionId)) }
        }

    /** Сохраняет токены [tokens], не трогая выбранный профиль. */
    private suspend fun saveTokens(tokens: ClientAppTokensResponse): BearerTokens {
        val session = store.load()?.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken) ?: Session(tokens.accessToken, tokens.refreshToken)
        store.save(session)
        return BearerTokens(tokens.accessToken, tokens.refreshToken)
    }

    /** Стирает сеанс на устройстве и токены в памяти клиента. */
    private suspend fun endSession() {
        store.clear()
        http.authProvider<BearerAuthProvider>()?.clearToken()
    }

    /**
     * Запрос [request] с разбором ответа в значение: 2xx — тело, 400 — [ApiError.Business] из
     * [ErrorResponse], остальное и сбой сети — [ApiError.Unavailable]. Исключения Ktor ловятся
     * только здесь, на границе с библиотекой.
     */
    private suspend inline fun <reified T> call(request: HttpClient.() -> HttpResponse): Either<ApiError, T> =
        try {
            val response = http.request()
            when {
                response.status.isSuccess() -> Either.Right(response.body<T>())
                response.status == HttpStatusCode.BadRequest -> response.body<ErrorResponse>().let { Either.Left(ApiError.Business(it.code, it.message)) }
                else -> Either.Left(ApiError.Unavailable)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Either.Left(ApiError.Unavailable)
        }
}

/** Тело запроса [body] в JSON. */
private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

/** Профиль [profile] в query-параметрах запроса данных клиента. */
private fun io.ktor.client.request.HttpRequestBuilder.profile(profile: ProfileKey) {
    parameter("orgId", profile.orgId.value.toString())
    parameter("clientId", profile.clientId.value.toString())
}
