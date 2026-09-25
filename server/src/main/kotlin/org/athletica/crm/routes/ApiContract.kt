package org.athletica.crm.routes

import io.ktor.http.HttpMethod
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Контракт HTTP-эндпоинта: метод, формат запроса и формат ответа.
 *
 * Записывается в атрибуты [Route] при регистрации маршрута. По дереву маршрутов
 * с этими записями генерируются контракты веб-клиента (`web/src/api/generated/contracts.ts`),
 * поэтому контракт есть у каждого маршрута под `/api`.
 */
data class ApiContract(
    /** HTTP-метод маршрута. */
    val method: HttpMethod,
    /** Формат тела или параметров запроса. */
    val request: ApiRequest,
    /** Формат тела ответа. */
    val response: ApiResponse,
)

/** Формат входных данных эндпоинта. */
sealed interface ApiRequest {
    /** Запрос без данных. */
    data object None : ApiRequest

    /** JSON-тело, декодируемое сериализатором [serializer]. */
    data class Body(val serializer: KSerializer<*>) : ApiRequest

    /** Плоские query-параметры, декодируемые сериализатором [serializer]. */
    data class Query(val serializer: KSerializer<*>) : ApiRequest

    /** Загрузка файла в `multipart/form-data`. */
    data object Multipart : ApiRequest
}

/** Формат ответа эндпоинта. */
sealed interface ApiResponse {
    /** Ответ без содержательного тела. */
    data object Empty : ApiResponse

    /** JSON-тело, кодируемое сериализатором [serializer]. */
    data class Json(val serializer: KSerializer<*>) : ApiResponse

    /** Файл для скачивания (не JSON). */
    data object File : ApiResponse
}

/** Ключ атрибута маршрута, под которым хранится [ApiContract]. */
val ApiContractKey = AttributeKey<ApiContract>("ApiContract")

/** Записывает контракт [contract] в атрибуты маршрута и возвращает сам маршрут. */
fun Route.contract(contract: ApiContract): Route =
    apply {
        attributes.put(ApiContractKey, contract)
    }

/** Контракт маршрута или `null`, если он не записан. */
val Route.apiContract: ApiContract?
    get() = attributes.getOrNull(ApiContractKey)

/** Формат JSON-тела запроса типа [T]; для [Unit] — запрос без данных. */
inline fun <reified T> bodyRequest(): ApiRequest = if (T::class == Unit::class) ApiRequest.None else ApiRequest.Body(serializer<T>())

/** Формат query-параметров запроса типа [T]; для [Unit] — запрос без данных. */
inline fun <reified T> queryRequest(): ApiRequest = if (T::class == Unit::class) ApiRequest.None else ApiRequest.Query(serializer<T>())

/** Формат ответа типа [T]: [Unit] — пустой ответ, [ByteArray] — файл, иначе JSON. */
inline fun <reified T> jsonResponse(): ApiResponse =
    when (T::class) {
        Unit::class -> ApiResponse.Empty
        ByteArray::class -> ApiResponse.File
        else -> ApiResponse.Json(serializer<T>())
    }

/** Контракт POST-маршрута с JSON-телом [Req] и ответом [Res]. */
inline fun <reified Req, reified Res> postContract(): ApiContract = ApiContract(HttpMethod.Post, bodyRequest<Req>(), jsonResponse<Res>())

/** Контракт GET-маршрута с query-параметрами [Req] и ответом [Res]. */
inline fun <reified Req, reified Res> getContract(): ApiContract = ApiContract(HttpMethod.Get, queryRequest<Req>(), jsonResponse<Res>())
