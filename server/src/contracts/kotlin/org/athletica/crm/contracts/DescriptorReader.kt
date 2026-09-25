package org.athletica.crm.contracts

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.getContextualDescriptor
import kotlinx.serialization.descriptors.getPolymorphicDescriptors
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule

/** Ошибка генерации контрактов: тип нельзя однозначно отобразить в TypeScript. */
class ContractGenerationException(message: String) : RuntimeException(message)

/** Дискриминатор полиморфизма по умолчанию — как у `Json` сервера. */
private const val DEFAULT_DISCRIMINATOR = "type"

/** Встроенные типы Kotlin, отображаемые безымянной схемой. */
private val INLINE_BUILTINS =
    mapOf(
        "kotlin.String" to "z.string()",
        "kotlin.Boolean" to "z.boolean()",
        "kotlin.Byte" to "z.int32()",
        "kotlin.Short" to "z.int32()",
        "kotlin.Int" to "z.int32()",
        "kotlin.Long" to "z.int()",
        "kotlin.Float" to "z.number()",
        "kotlin.Double" to "z.number()",
        "kotlin.Char" to "z.string().length(1)",
        "kotlin.uuid.Uuid" to "z.uuid()",
    )

/** Даты и время: строки ISO, у каждого типа свой бренд. */
private val TEMPORAL_BUILTINS =
    mapOf(
        "kotlin.time.Instant" to Definition.Scalar("kotlin.time.Instant", "Instant", "z.iso.datetime({ offset: true }).brand<\"Instant\">()"),
        "kotlinx.datetime.Instant" to Definition.Scalar("kotlin.time.Instant", "Instant", "z.iso.datetime({ offset: true }).brand<\"Instant\">()"),
        "kotlinx.datetime.LocalDate" to Definition.Scalar("kotlinx.datetime.LocalDate", "LocalDate", "z.iso.date().brand<\"LocalDate\">()"),
        "kotlinx.datetime.LocalTime" to Definition.Scalar("kotlinx.datetime.LocalTime", "LocalTime", "z.iso.time().brand<\"LocalTime\">()"),
        "kotlinx.datetime.LocalDateTime" to
            Definition.Scalar("kotlinx.datetime.LocalDateTime", "LocalDateTime", "z.iso.datetime({ local: true }).brand<\"LocalDateTime\">()"),
    )

/**
 * Строит определения схем по дескрипторам kotlinx.serialization.
 *
 * [module] — модуль сериализаторов для открытого полиморфизма и контекстных типов,
 * [customRules] — правила для типов со своим `KSerializer` (ключ — serial name дескриптора).
 * Тип со своим сериализатором без правила, неразрешимый полиморфизм и совпадение имён
 * разных типов приводят к [ContractGenerationException].
 */
@OptIn(ExperimentalSerializationApi::class)
class DescriptorReader(
    private val module: SerializersModule,
    private val customRules: Map<String, CustomRule>,
) {
    private val definitionsByKey = LinkedHashMap<String, Definition>()
    private val keysByName = HashMap<String, String>()
    private val inProgress = HashSet<String>()

    /** Все определения, найденные к этому моменту, в порядке обнаружения. */
    val definitions: Map<String, Definition>
        get() = definitionsByKey

    /** Выражение типа для [descriptor]; попутно регистрирует нужные определения. [site] — место использования для сообщений. */
    fun expr(descriptor: SerialDescriptor, site: String): TypeExpr {
        if (descriptor.isNullable) {
            return TypeExpr.Nullable(expr(descriptor.nonNullOriginal, site))
        }
        val serialName = descriptor.serialName
        val rule = customRules[serialName]
        val inline = INLINE_BUILTINS[serialName]
        val temporal = TEMPORAL_BUILTINS[serialName]
        return when {
            rule != null -> customExpr(descriptor, rule, site)
            inline != null -> TypeExpr.Inline(inline)
            temporal != null -> ref(temporal)
            descriptor.isInline -> inlineClassExpr(descriptor, site)
            else -> structuredExpr(descriptor, site)
        }
    }

    /** Выражение для типа со своим сериализатором по правилу [rule]. */
    private fun customExpr(descriptor: SerialDescriptor, rule: CustomRule, site: String): TypeExpr =
        when (rule) {
            is CustomRule.Inline -> TypeExpr.Inline(rule.zod)
            is CustomRule.Named -> ref(Definition.Scalar(descriptor.serialName, rule.name, rule.zod))
            is CustomRule.Renamed -> objectRef(descriptor, rule.name, site)
        }

    /** Выражение для value class: идентификатор на UUID — брендированная строка, остальное — нижележащий тип. */
    private fun inlineClassExpr(descriptor: SerialDescriptor, site: String): TypeExpr {
        val underlying = descriptor.getElementDescriptor(0)
        if (underlying.serialName != "kotlin.uuid.Uuid") {
            return expr(underlying, site)
        }
        val name = typeName(descriptor.serialName, site)
        return ref(Definition.Scalar(descriptor.serialName, name, "z.uuid().brand<\"$name\">()"))
    }

    /** Выражение для перечислений, коллекций, классов и полиморфных типов. */
    private fun structuredExpr(descriptor: SerialDescriptor, site: String): TypeExpr {
        val kind: SerialKind = descriptor.kind
        return when (kind) {
            is PrimitiveKind ->
                throw ContractGenerationException(
                    "Тип ${descriptor.serialName} ($site) сериализуется своим KSerializer, а правила отображения для него нет. " +
                        "Добавьте правило в CustomSerializerRules.kt",
                )
            SerialKind.ENUM ->
                ref(Definition.Enum(descriptor.serialName, typeName(descriptor.serialName, site), descriptor.elementNames.toList()))
            StructureKind.LIST -> TypeExpr.Array(expr(descriptor.getElementDescriptor(0), site))
            StructureKind.MAP -> mapExpr(descriptor, site)
            StructureKind.CLASS, StructureKind.OBJECT -> objectRef(descriptor, typeName(descriptor.serialName, site), site)
            PolymorphicKind.SEALED -> sealedRef(descriptor, site)
            PolymorphicKind.OPEN -> openRef(descriptor, site)
            SerialKind.CONTEXTUAL -> {
                val actual =
                    module.getContextualDescriptor(descriptor)
                        ?: throw ContractGenerationException("Контекстный тип ${descriptor.serialName} ($site) не зарегистрирован в модуле сериализаторов")
                expr(actual, site)
            }
        }
    }

    /** `Map<K, V>`: ключи — строки или перечисления. */
    private fun mapExpr(descriptor: SerialDescriptor, site: String): TypeExpr {
        val keyDescriptor = descriptor.getElementDescriptor(0)
        val value = expr(descriptor.getElementDescriptor(1), site)
        val key = expr(keyDescriptor, site)
        return when {
            keyDescriptor.isNullable -> throw ContractGenerationException("Map с nullable-ключом ($site) не поддерживается")
            keyDescriptor.kind == SerialKind.ENUM -> TypeExpr.Record(key, value, partial = true)
            key == TypeExpr.Inline("z.string()") -> TypeExpr.Record(key, value, partial = false)
            else -> throw ContractGenerationException("Map с ключом ${keyDescriptor.serialName} ($site) не поддерживается: ключ должен быть строкой или перечислением")
        }
    }

    /** Ссылка на объект [descriptor] с именем [name]; определение строится при первом обращении. */
    private fun objectRef(descriptor: SerialDescriptor, name: String, site: String): TypeExpr {
        val key = descriptor.serialName
        if (key !in definitionsByKey && key !in inProgress) {
            inProgress += key
            register(Definition.Object(key, name, fields(descriptor, name), discriminator = null))
            inProgress -= key
        }
        if (key in inProgress) {
            throw ContractGenerationException("Рекурсивный тип $key ($site) не поддерживается")
        }
        return TypeExpr.Ref(key)
    }

    /** Ссылка на sealed-иерархию [descriptor]: размеченное объединение её вариантов. */
    private fun sealedRef(descriptor: SerialDescriptor, site: String): TypeExpr {
        val variants = descriptor.getElementDescriptor(1).elementDescriptors.toList()
        return unionRef(descriptor, variants, site)
    }

    /** Ссылка на открытый полиморфный тип: варианты берутся из модуля сериализаторов. */
    private fun openRef(descriptor: SerialDescriptor, site: String): TypeExpr {
        val variants = module.getPolymorphicDescriptors(descriptor)
        if (variants.isEmpty()) {
            throw ContractGenerationException("Для полиморфного типа ${descriptor.serialName} ($site) в модуле сериализаторов нет вариантов")
        }
        return unionRef(descriptor, variants, site)
    }

    /** Ссылка на объединение [variants] полиморфного типа [descriptor]. */
    private fun unionRef(descriptor: SerialDescriptor, variants: List<SerialDescriptor>, site: String): TypeExpr {
        val key = descriptor.serialName
        if (variants.isEmpty()) {
            throw ContractGenerationException(
                "У полиморфного типа $key ($site) нет вариантов. Если у него свой KSerializer, добавьте правило в CustomSerializerRules.kt",
            )
        }
        if (key in inProgress) {
            throw ContractGenerationException("Рекурсивный тип $key ($site) не поддерживается")
        }
        if (key !in definitionsByKey) {
            inProgress += key
            val name = typeName(key, site)
            val discriminator = discriminator(descriptor)
            val variantKeys =
                variants.sortedBy { it.serialName }.map { variant ->
                    val variantKey = "$key#${variant.serialName}"
                    val variantName = name + pascalCase(variant.serialName.substringAfterLast('.'))
                    register(
                        Definition.Object(
                            key = variantKey,
                            name = variantName,
                            fields = fields(variant, variantName),
                            discriminator = discriminator to variant.serialName,
                        ),
                    )
                    variantKey
                }
            register(Definition.Union(key, name, discriminator, variantKeys))
            inProgress -= key
        }
        return TypeExpr.Ref(key)
    }

    /** Поля класса [descriptor] с именем [owner]. */
    private fun fields(descriptor: SerialDescriptor, owner: String): List<Field> =
        (0 until descriptor.elementsCount).map {
            val fieldName = descriptor.getElementName(it)
            Field(
                name = fieldName,
                type = expr(descriptor.getElementDescriptor(it), "$owner.$fieldName"),
                hasDefault = descriptor.isElementOptional(it),
            )
        }

    /** Имя поля-дискриминатора полиморфного типа [descriptor]. */
    private fun discriminator(descriptor: SerialDescriptor): String =
        descriptor.annotations
            .filterIsInstance<JsonClassDiscriminator>()
            .firstOrNull()
            ?.discriminator ?: DEFAULT_DISCRIMINATOR

    /** Регистрирует готовое определение [definition] и возвращает ссылку на него. */
    private fun ref(definition: Definition): TypeExpr {
        register(definition)
        return TypeExpr.Ref(definition.key)
    }

    /** Добавляет определение; одно имя у разных ключей — ошибка. */
    private fun register(definition: Definition) {
        val owner = keysByName.putIfAbsent(definition.name, definition.key)
        if (owner != null && owner != definition.key) {
            throw ContractGenerationException(
                "Типы $owner и ${definition.key} получают одно имя ${definition.name} в TypeScript. Переименуйте один из них в Kotlin",
            )
        }
        definitionsByKey.putIfAbsent(definition.key, definition)
    }
}

/**
 * Имя TypeScript-типа по serial name Kotlin-класса: сегменты после пакета, склеенные
 * без точек (`org.x.CustomFieldValue.Text` → `CustomFieldValueText`).
 * [site] — место использования для сообщения об ошибке.
 */
fun typeName(serialName: String, site: String): String {
    val classSegments = serialName.split('.').dropWhile { segment -> segment.firstOrNull()?.isUpperCase() != true }
    if (classSegments.isEmpty() || !serialName.contains('.')) {
        throw ContractGenerationException(
            "Не удаётся построить имя типа по serial name \"$serialName\" ($site): ожидается полное имя класса",
        )
    }
    return classSegments.joinToString("") { it.filter(Char::isLetterOrDigit) }
}

/** `twilio_sms` → `TwilioSms`, `Text` → `Text`. */
fun pascalCase(value: String): String =
    value
        .split('_', '-', ' ')
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
