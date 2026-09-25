package org.athletica.crm.contracts

/** Роль типа в контракте: в теле запроса или в ответе. */
enum class Role {
    Request,
    Response,
}

/** Выражение типа поля в терминах генерируемых схем. */
sealed interface TypeExpr {
    /** Схема без имени, вставляется выражением zod [zod] как есть. */
    data class Inline(val zod: String) : TypeExpr

    /** Ссылка на именованное определение с ключом [key]. */
    data class Ref(val key: String) : TypeExpr

    /** Значение [inner] или `null`. */
    data class Nullable(val inner: TypeExpr) : TypeExpr

    /** Массив только для чтения из [element]. */
    data class Array(val element: TypeExpr) : TypeExpr

    /**
     * Объект только для чтения с ключами [key] и значениями [value].
     * [partial] — не все ключи обязательны (ключи-перечисления).
     */
    data class Record(val key: TypeExpr, val value: TypeExpr, val partial: Boolean) : TypeExpr
}

/** Поле объекта: имя [name], тип [type], [hasDefault] — у поля есть значение по умолчанию. */
data class Field(
    /** Имя поля в JSON. */
    val name: String,
    /** Тип значения поля. */
    val type: TypeExpr,
    /** У поля есть значение по умолчанию; в запросе его можно не передавать. */
    val hasDefault: Boolean,
)

/** Именованное определение в сгенерированном файле. */
sealed interface Definition {
    /** Уникальный ключ определения (обычно serial name Kotlin-типа). */
    val key: String

    /** Имя типа в TypeScript; схема называется `${name}Schema`. */
    val name: String

    /** Схема, заданная готовым выражением zod [zod] (идентификаторы, даты, свои сериализаторы). */
    data class Scalar(override val key: String, override val name: String, val zod: String) : Definition

    /** Перечисление строковых литералов [values]. */
    data class Enum(override val key: String, override val name: String, val values: List<String>) : Definition

    /**
     * Объект с полями [fields]. Для варианта размеченного объединения [discriminator] —
     * пара «имя поля-дискриминатора — значение».
     */
    data class Object(
        override val key: String,
        override val name: String,
        val fields: List<Field>,
        val discriminator: Pair<String, String>?,
    ) : Definition

    /** Размеченное объединение вариантов [variants] (ключи [Object]) по полю [discriminator]. */
    data class Union(
        override val key: String,
        override val name: String,
        val discriminator: String,
        val variants: List<String>,
    ) : Definition
}

/** Ссылки на определения, которые использует выражение. */
fun TypeExpr.refs(): List<String> =
    when (this) {
        is TypeExpr.Inline -> emptyList()
        is TypeExpr.Ref -> listOf(key)
        is TypeExpr.Nullable -> inner.refs()
        is TypeExpr.Array -> element.refs()
        is TypeExpr.Record -> key.refs() + value.refs()
    }

/** Ключи определений, от которых зависит определение. */
fun Definition.dependencies(): List<String> =
    when (this) {
        is Definition.Scalar, is Definition.Enum -> emptyList()
        is Definition.Object -> fields.flatMap { it.type.refs() }
        is Definition.Union -> variants
    }
