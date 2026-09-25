package org.athletica.crm.contracts

/**
 * Эндпоинт в терминах схем: путь [path], метод [method], формат входа [input] и выхода [output].
 * [request] и [response] заданы только для JSON-тела или query-параметров.
 */
data class EndpointSchema(
    /** Путь без префикса `/api/`. */
    val path: String,
    /** HTTP-метод: `GET` или `POST`. */
    val method: String,
    /** Формат входа: `none`, `body`, `query` или `multipart`. */
    val input: String,
    /** Тип запроса для `body` и `query`. */
    val request: TypeExpr?,
    /** Формат ответа: `empty`, `json` или `file`. */
    val output: String,
    /** Тип ответа для `json`. */
    val response: TypeExpr?,
)

/** Регулярное выражение имени свойства, которое можно не заключать в кавычки. */
private val IDENTIFIER = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

/**
 * Печатает `contracts.ts` по определениям [definitions].
 *
 * Роль каждого определения (запрос, ответ или обе) выводится обходом графа типов
 * от эндпоинтов. Поля со значением по умолчанию необязательны только в запросах;
 * если такой тип встречается в обеих ролях, печатаются две схемы: `Xxx` (ответ)
 * и `XxxInput` (запрос). [extraResponses] — типы ответов вне эндпоинтов (например, ошибка).
 */
class ContractRenderer(
    private val definitions: Map<String, Definition>,
) {
    private val roles = HashMap<String, MutableSet<Role>>()
    private val sensitivity = HashMap<String, Boolean>()

    /** Текст модуля с определениями и объектом `endpoints` для [endpoints]. */
    fun render(endpoints: List<EndpointSchema>, extraResponses: List<TypeExpr>): String {
        endpoints.forEach { endpoint ->
            endpoint.request?.let { markRoles(it, Role.Request) }
            endpoint.response?.let { markRoles(it, Role.Response) }
        }
        extraResponses.forEach { markRoles(it, Role.Response) }

        val emitted = LinkedHashMap<String, Pair<String, Role>>()
        instances()
            .sortedBy { (key, role) -> instanceName(key, role) }
            .forEach { emitWithDependencies(it, emitted) }

        val duplicates = emitted.keys.groupBy { it.lowercase() }.filterValues { it.size > 1 }.values.flatten()
        if (duplicates.isNotEmpty()) {
            throw ContractGenerationException("Совпадение имён сгенерированных схем: ${duplicates.joinToString()}")
        }
        val shadowed = emitted.keys.filter { it.endsWith("Schema") && it.removeSuffix("Schema") in emitted }
        if (shadowed.isNotEmpty()) {
            throw ContractGenerationException("Имя типа совпадает с именем схемы другого типа: ${shadowed.joinToString()}")
        }

        return buildString {
            appendLine("// Сгенерировано задачей :server:generateWebContracts из маршрутов сервера и схем shared.")
            appendLine("// Не редактировать вручную: после изменения схем выполните `npm run contracts`.")
            appendLine()
            appendLine("import { z } from \"zod\";")
            emitted.values.forEach { (key, role) ->
                appendLine()
                append(definitionText(key, role))
            }
            appendLine()
            append(endpointsText(endpoints))
        }
    }

    /** Отмечает роль [role] у всех определений, достижимых из [expr]. */
    private fun markRoles(expr: TypeExpr, role: Role) {
        expr.refs().forEach { markRoles(it, role) }
    }

    /** Отмечает роль [role] у определения [key] и его зависимостей. */
    private fun markRoles(key: String, role: Role) {
        if (roles.getOrPut(key) { mutableSetOf() }.add(role)) {
            definition(key).dependencies().forEach { markRoles(it, role) }
        }
    }

    /** Истина, если схема определения [key] зависит от роли (есть поля со значением по умолчанию). */
    private fun isRoleSensitive(key: String): Boolean =
        sensitivity.getOrPut(key) {
            when (val definition = definition(key)) {
                is Definition.Scalar, is Definition.Enum -> false
                is Definition.Object -> definition.fields.any { it.hasDefault } || definition.dependencies().any { isRoleSensitive(it) }
                is Definition.Union -> definition.variants.any { isRoleSensitive(it) }
            }
        }

    /** Экземпляры схем к печати: по одному на роль для зависимых от роли определений, иначе один. */
    private fun instances(): List<Pair<String, Role>> =
        roles.flatMap { (key, keyRoles) ->
            if (isRoleSensitive(key)) {
                keyRoles.map { key to it }
            } else {
                listOf(key to keyRoles.min())
            }
        }

    /** Добавляет экземпляр [instance] в [emitted] после всех его зависимостей. */
    private fun emitWithDependencies(instance: Pair<String, Role>, emitted: LinkedHashMap<String, Pair<String, Role>>) {
        val (key, role) = instance
        val name = instanceName(key, role)
        if (name in emitted) {
            return
        }
        definition(key).dependencies().sorted().forEach { emitWithDependencies(it to role, emitted) }
        emitted[name] = instance
    }

    /** Имя экземпляра схемы определения [key] в роли [role]. */
    private fun instanceName(key: String, role: Role): String {
        val definition = definition(key)
        val needsInputSuffix = role == Role.Request && isRoleSensitive(key) && Role.Response in roles[key].orEmpty()
        return if (needsInputSuffix) "${definition.name}Input" else definition.name
    }

    /** Определение по ключу [key]. */
    private fun definition(key: String): Definition = definitions[key] ?: throw ContractGenerationException("Определение $key не найдено")

    /** Текст схемы и типа для экземпляра [key] в роли [role]. */
    private fun definitionText(key: String, role: Role): String {
        val name = instanceName(key, role)
        val schema =
            when (val definition = definition(key)) {
                is Definition.Scalar -> definition.zod
                is Definition.Enum -> definition.values.joinToString(", ", prefix = "z.enum([", postfix = "])") { literal(it) }
                is Definition.Object -> objectText(definition, role)
                is Definition.Union ->
                    definition.variants.joinToString(
                        separator = ", ",
                        prefix = "z.discriminatedUnion(${literal(definition.discriminator)}, [",
                        postfix = "])",
                    ) { "${instanceName(it, role)}Schema" }
            }
        return buildString {
            appendLine("export const ${name}Schema = $schema;")
            appendLine("export type $name = z.output<typeof ${name}Schema>;")
        }
    }

    /** Текст `z.object(...)` для [definition] в роли [role]. */
    private fun objectText(definition: Definition.Object, role: Role): String {
        val discriminator = definition.discriminator?.let { (field, value) -> "  ${property(field)}: z.literal(${literal(value)}),\n" }.orEmpty()
        val fields =
            definition.fields.joinToString("") { field ->
                val optional = if (role == Role.Request && field.hasDefault) ".optional()" else ""
                "  ${property(field.name)}: ${exprText(field.type, role)}$optional,\n"
            }
        val body = discriminator + fields
        return if (body.isEmpty()) "z.object({}).readonly()" else "z\n  .object({\n${body.trimEnd('\n').prependIndent("  ")}\n  })\n  .readonly()"
    }

    /** Текст выражения zod для [expr] в роли [role]. */
    private fun exprText(expr: TypeExpr, role: Role): String =
        when (expr) {
            is TypeExpr.Inline -> expr.zod
            is TypeExpr.Ref -> "${instanceName(expr.key, role)}Schema"
            is TypeExpr.Nullable -> "${exprText(expr.inner, role)}.nullable()"
            is TypeExpr.Array -> "z.array(${exprText(expr.element, role)}).readonly()"
            is TypeExpr.Record -> {
                val function = if (expr.partial) "z.partialRecord" else "z.record"
                "$function(${exprText(expr.key, role)}, ${exprText(expr.value, role)}).readonly()"
            }
        }

    /** Текст объекта `endpoints` для [endpoints]. */
    private fun endpointsText(endpoints: List<EndpointSchema>): String =
        buildString {
            appendLine("/** Все эндпоинты API: путь без `/api/` → метод, формат запроса и ответа. */")
            appendLine("export const endpoints = {")
            endpoints.forEach { endpoint ->
                val request = endpoint.request?.let { exprText(it, Role.Request) } ?: payloadlessSchema(endpoint.input)
                val response = endpoint.response?.let { exprText(it, Role.Response) } ?: payloadlessSchema(endpoint.output)
                appendLine("  ${literal(endpoint.path)}: {")
                appendLine("    method: ${literal(endpoint.method)},")
                appendLine("    in: ${literal(endpoint.input)},")
                appendLine("    request: $request,")
                appendLine("    out: ${literal(endpoint.output)},")
                appendLine("    response: $response,")
                appendLine("  },")
            }
            appendLine("} as const;")
        }

    /**
     * Схема входа или выхода без JSON: `none`/`empty` — отсутствие данных, `multipart` — `FormData`,
     * `file` — `Blob`. Благодаря этому у каждого эндпоинта `request` и `response` — схемы,
     * и тип данных выводится из них одинаково.
     */
    private fun payloadlessSchema(format: String): String =
        when (format) {
            "none", "empty" -> "z.undefined()"
            "multipart" -> "z.instanceof(FormData)"
            "file" -> "z.instanceof(Blob)"
            else -> throw ContractGenerationException("Формат $format требует схемы")
        }

    /** Имя свойства объекта: без кавычек, если это идентификатор. */
    private fun property(name: String): String = if (IDENTIFIER.matches(name)) name else literal(name)

    /** Строковый литерал TypeScript. */
    private fun literal(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
