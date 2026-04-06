package org.athletica.crm.i18n

import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext

interface LocalizationKey {
    val ru: String
    val en: String

    fun localize(lang: Lang): String =
        when (lang) {
            Lang.EN -> en
            Lang.RU -> ru
        }

    context(ctx: RequestContext)
    fun localize(): String = localize(ctx.lang)
}

interface LocalizationTemplate1<A> {
    val ru: (A) -> String
    val en: (A) -> String

    fun localize(lang: Lang, a: A): String =
        when (lang) {
            Lang.EN -> en(a)
            Lang.RU -> ru(a)
        }

    context(ctx: RequestContext)
    fun localize(a: A): String = localize(ctx.lang, a)
}

interface LocalizationTemplate2<A, B> {
    val ru: (A, B) -> String
    val en: (A, B) -> String

    fun localize(lang: Lang, a: A, b: B): String =
        when (lang) {
            Lang.EN -> en(a, b)
            Lang.RU -> ru(a, b)
        }

    context(ctx: RequestContext)
    fun localize(a: A, b: B): String = localize(ctx.lang, a, b)
}

data class PluralForms(
    val one: (Int) -> String,
    val few: ((Int) -> String)? = null,
    val many: (Int) -> String,
) {
    fun select(lang: Lang, count: Int): String =
        when (lang) {
            Lang.EN -> if (count == 1) one(count) else many(count)
            Lang.RU -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod100 in 11..19 -> many(count)
                    mod10 == 1 -> one(count)
                    mod10 in 2..4 -> (few ?: many)(count)
                    else -> many(count)
                }
            }
        }
}

interface PluralKey {
    val ru: PluralForms
    val en: PluralForms

    fun localize(lang: Lang, count: Int): String =
        when (lang) {
            Lang.EN -> en.select(lang, count)
            Lang.RU -> ru.select(lang, count)
        }

    context(ctx: RequestContext)
    fun localize(count: Int): String = localize(ctx.lang, count)
}

object Messages {
    object MissingParameterId : LocalizationKey {
        override val ru = "Параметр id обязателен"
        override val en = "Parameter id is required"
    }

    object InvalidParameterId : LocalizationKey {
        override val ru = "Параметр id должен быть корректным UUID"
        override val en = "Parameter id must be a valid UUID"
    }

    object WrongPassword : LocalizationKey {
        override val ru = "Неверный текущий пароль"
        override val en = "Incorrect current password"
    }

    object OrgNameBlank : LocalizationKey {
        override val ru = "Название организации не может быть пустым"
        override val en = "Organization name cannot be blank"
    }

    object OrgNotFound : LocalizationKey {
        override val ru = "Организация не найдена"
        override val en = "Organization not found"
    }

    object ClientAlreadyExists : LocalizationKey {
        override val ru = "Клиент с таким идентификатором уже существует"
        override val en = "Client with this ID already exists"
    }

    object ClientNotFound : LocalizationKey {
        override val ru = "Клиент не найден"
        override val en = "Client not found"
    }

    object DisciplineAlreadyExists : LocalizationKey {
        override val ru = "Дисциплина с таким названием уже существует"
        override val en = "Discipline with this name already exists"
    }

    object DisciplineNotFound : LocalizationKey {
        override val ru = "Дисциплина не найдена"
        override val en = "Discipline not found"
    }

    object GroupAlreadyExists : LocalizationKey {
        override val ru = "Группа с таким идентификатором уже существует"
        override val en = "Group with this ID already exists"
    }

    object InvalidScheduleStartTime : LocalizationTemplate1<String> {
        override val ru = { time: String -> "Некорректное время начала слота: \"$time\"" }
        override val en = { time: String -> "Invalid slot start time: \"$time\"" }
    }

    object InvalidScheduleEndTime : LocalizationTemplate1<String> {
        override val ru = { time: String -> "Некорректное время окончания слота: \"$time\"" }
        override val en = { time: String -> "Invalid slot end time: \"$time\"" }
    }

    object ScheduleEndBeforeStart : LocalizationTemplate2<String, String> {
        override val ru = { start: String, end: String -> "Время окончания должно быть позже времени начала: $start – $end" }
        override val en = { start: String, end: String -> "End time must be after start time: $start – $end" }
    }

    object EmptyFile : LocalizationKey {
        override val ru = "Файл не может быть пустым"
        override val en = "File cannot be empty"
    }

    object FileNotInRequest : LocalizationKey {
        override val ru = "Файл не найден в запросе"
        override val en = "File not found in request"
    }

    object UploadNotFound : LocalizationKey {
        override val ru = "Загрузка не найдена"
        override val en = "Upload not found"
    }
}
