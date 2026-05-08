package org.athletica.crm.api.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue

/**
 * Модуль сериализаторов с явной регистрацией полиморфных подтипов.
 * Необходим для WasmJS, где [kotlinx.serialization] не находит сабтайпы автоматически
 * через [SealedClassSerializer] и требует явной регистрации в [SerializersModule].
 */
val appSerializersModule =
    SerializersModule {
        polymorphic(CustomFieldValue::class) {
            subclass(CustomFieldValue.Text::class, CustomFieldValue.Text.serializer())
            subclass(CustomFieldValue.Number::class, CustomFieldValue.Number.serializer())
            subclass(CustomFieldValue.Bool::class, CustomFieldValue.Bool.serializer())
            subclass(CustomFieldValue.Date::class, CustomFieldValue.Date.serializer())
            subclass(CustomFieldValue.Select::class, CustomFieldValue.Select.serializer())
        }
        polymorphic(CustomFieldDefinition::class) {
            subclass(CustomFieldDefinition.Text::class, CustomFieldDefinition.Text.serializer())
            subclass(CustomFieldDefinition.Number::class, CustomFieldDefinition.Number.serializer())
            subclass(CustomFieldDefinition.Date::class, CustomFieldDefinition.Date.serializer())
            subclass(CustomFieldDefinition.Bool::class, CustomFieldDefinition.Bool.serializer())
            subclass(CustomFieldDefinition.Phone::class, CustomFieldDefinition.Phone.serializer())
            subclass(CustomFieldDefinition.Email::class, CustomFieldDefinition.Email.serializer())
            subclass(CustomFieldDefinition.Url::class, CustomFieldDefinition.Url.serializer())
            subclass(CustomFieldDefinition.Select::class, CustomFieldDefinition.Select.serializer())
        }
    }

/** Общий [Json] для API-клиента на всех платформах. */
val appJson =
    Json {
        ignoreUnknownKeys = true
        serializersModule = appSerializersModule
    }
