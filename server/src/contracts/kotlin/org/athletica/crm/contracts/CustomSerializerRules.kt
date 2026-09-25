package org.athletica.crm.contracts

import kotlinx.serialization.json.JsonElement
import org.athletica.crm.api.schemas.schedule.SessionColorKey
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.customfields.CustomFieldKey
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.time.Validity

/** Правило отображения типа со своим `KSerializer` в TypeScript. */
sealed interface CustomRule {
    /** Безымянная схема — выражение zod [zod]. */
    data class Inline(val zod: String) : CustomRule

    /** Именованная схема [name] с выражением zod [zod]. */
    data class Named(val name: String, val zod: String) : CustomRule

    /** Сериализатор пишет обычный объект-суррогат; он генерируется как объект с именем [name]. */
    data class Renamed(val name: String) : CustomRule
}

/** Литералы [values] в виде `z.enum([...])`. */
private fun zodEnum(values: List<String>): String = values.joinToString(", ", prefix = "z.enum([", postfix = "])") { "\"$it\"" }

/**
 * Правила для всех типов `shared` со своим `KSerializer`; ключ — serial name дескриптора.
 * Новый тип со своим сериализатором без записи здесь роняет генерацию.
 */
val customSerializerRules: Map<String, CustomRule> =
    mapOf(
        Currency.serializer().descriptor.serialName to CustomRule.Named("Currency", zodEnum(Currency.entries.map { it.code })),
        CustomFieldKey.serializer().descriptor.serialName to
            CustomRule.Named("CustomFieldKey", "z.string().regex(/^[a-z_]+$/).brand<\"CustomFieldKey\">()"),
        ClientNoteText.serializer().descriptor.serialName to CustomRule.Inline("z.string().max(${ClientNoteText.MAX_LENGTH})"),
        SessionColorKey.serializer().descriptor.serialName to CustomRule.Named("SessionColorKey", zodEnum(SessionColorKey.entries.map { it.name })),
        Validity.serializer().descriptor.serialName to CustomRule.Renamed("Validity"),
        JsonElement.serializer().descriptor.serialName to CustomRule.Named("JsonValue", "z.json()"),
    )
