package org.athletica.crm.api.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.athletica.crm.api.schemas.customfields.CustomFieldValue

/**
 * Модуль сериализаторов с явной регистрацией полиморфных подтипов [CustomFieldValue].
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
    }

/** Общий [Json] для API-клиента на всех платформах. */
val appJson =
    Json {
        ignoreUnknownKeys = true
        serializersModule = appSerializersModule
    }
