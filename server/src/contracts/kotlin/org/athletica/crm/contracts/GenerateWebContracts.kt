package org.athletica.crm.contracts

import kotlinx.serialization.modules.SerializersModule
import org.athletica.crm.api.client.appSerializersModule
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.routes.ApiRequest
import org.athletica.crm.routes.ApiResponse
import java.io.File
import kotlin.system.exitProcess

/**
 * Генерирует `contracts.ts` веб-клиента. Единственный аргумент — путь к выходному файлу.
 * При нарушении инвариантов печатает причину и завершается с кодом 1.
 */
fun main(args: Array<String>) {
    val output =
        args.singleOrNull() ?: run {
            System.err.println("Использование: GenerateWebContracts <путь к contracts.ts>")
            exitProcess(2)
        }
    val text =
        try {
            contractsText(endpoints(registeredRoutes(offlineDi())))
        } catch (e: ManifestException) {
            System.err.println(e.message)
            exitProcess(1)
        } catch (e: ContractGenerationException) {
            System.err.println("Генерация контрактов: ${e.message}")
            exitProcess(1)
        }
    File(output).apply {
        parentFile.mkdirs()
        writeText(text)
    }
    println("Контракты записаны в $output")
    exitProcess(0)
}

/** Текст `contracts.ts` для [endpoints]; [module] разрешает открытый полиморфизм. */
fun contractsText(endpoints: List<Endpoint>, module: SerializersModule = appSerializersModule): String {
    val reader = DescriptorReader(module, customSerializerRules)
    val schemas = endpoints.map { reader.endpointSchema(it) }
    val errorResponse = reader.expr(ErrorResponse.serializer().descriptor, "ErrorResponse")
    return ContractRenderer(reader.definitions).render(schemas, extraResponses = listOf(errorResponse))
}

/** Схема эндпоинта [endpoint] с зарегистрированными определениями запроса и ответа. */
private fun DescriptorReader.endpointSchema(endpoint: Endpoint): EndpointSchema {
    val site = endpoint.path
    val (input, request) =
        when (val request = endpoint.contract.request) {
            ApiRequest.None -> "none" to null
            ApiRequest.Multipart -> "multipart" to null
            is ApiRequest.Body -> "body" to expr(request.serializer.descriptor, "$site (запрос)")
            is ApiRequest.Query -> "query" to expr(request.serializer.descriptor, "$site (запрос)")
        }
    val (output, response) =
        when (val response = endpoint.contract.response) {
            ApiResponse.Empty -> "empty" to null
            ApiResponse.File -> "file" to null
            is ApiResponse.Json -> "json" to expr(response.serializer.descriptor, "$site (ответ)")
        }
    return EndpointSchema(site, endpoint.contract.method.value, input, request, output, response)
}
