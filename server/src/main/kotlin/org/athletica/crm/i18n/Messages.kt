package org.athletica.crm.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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

    object EmployeeAlreadyExists : LocalizationKey {
        override val ru = "Сотрудник с таким идентификатором уже существует"
        override val en = "Employee with this ID already exists"
    }

    object EmployeeEmailRequired : LocalizationKey {
        override val ru = "Email обязателен для создания сотрудника"
        override val en = "Email is required to create an employee"
    }

    object EmployeeEmailInUse : LocalizationKey {
        override val ru = "Пользователь с таким email уже зарегистрирован"
        override val en = "A user with this email is already registered"
    }

    object EmployeeNotFound : LocalizationKey {
        override val ru = "Сотрудник не найден"
        override val en = "Employee not found"
    }

    object DisciplineNotFound : LocalizationKey {
        override val ru = "Дисциплина не найдена"
        override val en = "Discipline not found"
    }

    object GroupAlreadyExists : LocalizationKey {
        override val ru = "Группа с таким идентификатором уже существует"
        override val en = "Group with this ID already exists"
    }

    object GroupNameAlreadyExists : LocalizationKey {
        override val ru = "Группа с таким названием уже существует"
        override val en = "Group with this name already exists"
    }

    object GroupNotFound : LocalizationKey {
        override val ru = "Группа не найдена"
        override val en = "Group not found"
    }

    object InvalidScheduleStartTime : LocalizationTemplate1<String> {
        override val ru = { time: String -> "Некорректное время начала слота: \"$time\"" }
        override val en = { time: String -> "Invalid slot start time: \"$time\"" }
    }

    object InvalidScheduleEndTime : LocalizationTemplate1<String> {
        override val ru = { time: String -> "Некорректное время окончания слота: \"$time\"" }
        override val en = { time: String -> "Invalid slot end time: \"$time\"" }
    }

    object ScheduleEffectiveFromInPast : LocalizationKey {
        override val ru = "Расписание нельзя изменить задним числом"
        override val en = "Schedule cannot be changed retroactively"
    }

    object DuplicateScheduleSlot : LocalizationTemplate2<String, LocalTime> {
        override val ru = { day: String, time: LocalTime -> "Слот повторяется в расписании: $day $time" }
        override val en = { day: String, time: LocalTime -> "Duplicate schedule slot: $day $time" }
    }

    object InvalidSchedulePeriod : LocalizationKey {
        override val ru = "Дата окончания периода не может быть раньше даты начала"
        override val en = "Period end date cannot precede its start date"
    }

    object SchedulePeriodTooLong : LocalizationTemplate1<Int> {
        override val ru = { days: Int -> "Период расписания не может превышать $days дней" }
        override val en = { days: Int -> "Schedule period cannot exceed $days days" }
    }

    object ScheduleEndBeforeStart : LocalizationTemplate2<LocalTime, LocalTime> {
        override val ru = { start: LocalTime, end: LocalTime -> "Время окончания должно быть позже времени начала: $start – $end" }
        override val en = { start: LocalTime, end: LocalTime -> "End time must be after start time: $start – $end" }
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

    object BalanceAmountZero : LocalizationKey {
        override val ru = "Сумма корректировки не может быть нулевой"
        override val en = "Adjustment amount cannot be zero"
    }

    object BalanceNoteRequired : LocalizationKey {
        override val ru = "Комментарий к корректировке обязателен"
        override val en = "Adjustment note is required"
    }

    object BalanceCurrencyMismatch : LocalizationKey {
        override val ru = "Валюта суммы не совпадает с валютой организации"
        override val en = "Amount currency does not match organization currency"
    }

    object DefaultBranchName : LocalizationKey {
        override val ru = "Основной"
        override val en = "Main"
    }

    object DefaultHallName : LocalizationKey {
        override val ru = "Основной"
        override val en = "Main"
    }

    object BranchAlreadyExists : LocalizationKey {
        override val ru = "Филиал с таким названием уже существует"
        override val en = "Branch with this name already exists"
    }

    object BranchNotFound : LocalizationKey {
        override val ru = "Филиал не найден"
        override val en = "Branch not found"
    }

    object HallAlreadyExists : LocalizationKey {
        override val ru = "Зал с таким названием уже существует"
        override val en = "Hall with this name already exists"
    }

    object HallNotFound : LocalizationKey {
        override val ru = "Зал не найден"
        override val en = "Hall not found"
    }

    object HallInUse : LocalizationKey {
        override val ru = "Зал используется в расписании или занятиях"
        override val en = "Hall is used in schedule or sessions"
    }

    object LeadSourceAlreadyExists : LocalizationKey {
        override val ru = "Источник с таким названием уже существует"
        override val en = "Lead source with this name already exists"
    }

    object LeadSourceNotFound : LocalizationKey {
        override val ru = "Источник не найден"
        override val en = "Lead source not found"
    }

    object LeadSourceDescriptionTooLong : LocalizationTemplate1<Int> {
        override val ru = { max: Int -> "Пояснение не может быть длиннее $max символов" }
        override val en = { max: Int -> "Description cannot be longer than $max characters" }
    }

    object MissingParameterEntityType : LocalizationKey {
        override val ru = "Параметр entityType обязателен"
        override val en = "Parameter entityType is required"
    }

    object InvalidFieldKey : LocalizationKey {
        override val ru = "Ключ поля должен содержать только строчные латинские буквы и символ подчёркивания"
        override val en = "Field key must contain only lowercase letters and underscores"
    }

    object AttendanceLabelAlreadyExists : LocalizationKey {
        override val ru = "Метка с таким названием уже существует"
        override val en = "Attendance label with this name already exists"
    }

    object AttendanceLabelNotFound : LocalizationKey {
        override val ru = "Метка посещаемости не найдена"
        override val en = "Attendance label not found"
    }

    object AttendanceLabelNameBlank : LocalizationKey {
        override val ru = "Название метки не может быть пустым"
        override val en = "Attendance label name must not be blank"
    }

    object AttendanceLabelNotApplicable : LocalizationKey {
        override val ru = "Метка не применима к выбранному состоянию отметки"
        override val en = "Label is not applicable to the selected attendance state"
    }

    object AttendanceLabelArchived : LocalizationKey {
        override val ru = "Архивную метку нельзя назначить новой отметке"
        override val en = "Archived label cannot be assigned to a new mark"
    }

    object AttendanceParticipantNotFound : LocalizationKey {
        override val ru = "Клиент не числится в составе занятия"
        override val en = "Client is not a participant of the session"
    }

    object AttendanceParticipantAlreadyExists : LocalizationKey {
        override val ru = "Клиент уже числится в составе занятия"
        override val en = "Client is already a participant of the session"
    }

    object AttendanceCannotRemoveRegular : LocalizationKey {
        override val ru = "Постоянного участника группы нельзя убрать из состава занятия"
        override val en = "A regular group member cannot be removed from the session"
    }

    object AttendanceJournalClosed : LocalizationKey {
        override val ru = "Состав можно менять только у запланированного занятия"
        override val en = "Participants can only be changed for a scheduled session"
    }

    object AttendanceSessionCancelled : LocalizationKey {
        override val ru = "Занятие отменено, журнал недоступен"
        override val en = "Session is cancelled, the journal is unavailable"
    }

    object AttendanceCannotUnmarkCompleted : LocalizationKey {
        override val ru = "В проведённом занятии отметку нельзя снять"
        override val en = "A mark cannot be cleared in a completed session"
    }

    object SessionJournalIncomplete : LocalizationKey {
        override val ru = "Журнал не заполнен: есть неотмеченные участники"
        override val en = "The journal is incomplete: some participants are not marked"
    }

    object SessionNotStarted : LocalizationKey {
        override val ru = "Занятие ещё не началось"
        override val en = "The session has not started yet"
    }

    object InvalidAttendancePeriod : LocalizationKey {
        override val ru = "Конец периода раньше начала"
        override val en = "Period end is before its start"
    }

    object AttendancePeriodTooLong : LocalizationKey {
        override val ru = "Период посещаемости не может быть длиннее года"
        override val en = "Attendance period cannot exceed one year"
    }

    object DefaultAttendanceLabelLate : LocalizationKey {
        override val ru = "Опоздал"
        override val en = "Late"
    }

    object DefaultAttendanceLabelLeftEarly : LocalizationKey {
        override val ru = "Ушёл раньше"
        override val en = "Left early"
    }

    object DefaultAttendanceLabelSick : LocalizationKey {
        override val ru = "Болеет"
        override val en = "Sick"
    }

    object DefaultAttendanceLabelExcused : LocalizationKey {
        override val ru = "Предупредил"
        override val en = "Excused"
    }

    object DefaultAttendanceLabelTruant : LocalizationKey {
        override val ru = "Прогул"
        override val en = "Truant"
    }

    object DefaultLeadSourceReferral : LocalizationKey {
        override val ru = "Рекомендация"
        override val en = "Referral"
    }

    object DefaultLeadSourceReferralDescription : LocalizationKey {
        override val ru = "Посоветовали друзья, знакомые или действующие клиенты"
        override val en = "Recommended by friends, acquaintances or current clients"
    }

    object DefaultLeadSourceSocialMedia : LocalizationKey {
        override val ru = "Соцсети"
        override val en = "Social media"
    }

    object DefaultLeadSourceSocialMediaDescription : LocalizationKey {
        override val ru = "Публикации и страницы в соцсетях, кроме платной рекламы"
        override val en = "Posts and pages on social networks, excluding paid ads"
    }

    object DefaultLeadSourcePaidSocialAds : LocalizationKey {
        override val ru = "Таргетированная реклама"
        override val en = "Paid social ads"
    }

    object DefaultLeadSourcePaidSocialAdsDescription : LocalizationKey {
        override val ru = "Платная реклама в соцсетях"
        override val en = "Paid advertising on social networks"
    }

    object DefaultLeadSourceWebSearch : LocalizationKey {
        override val ru = "Поиск в интернете"
        override val en = "Web search"
    }

    object DefaultLeadSourceWebSearchDescription : LocalizationKey {
        override val ru = "Поисковые системы, включая контекстную рекламу"
        override val en = "Search engines, including search ads"
    }

    object DefaultLeadSourceMapsAndReviews : LocalizationKey {
        override val ru = "Карты и отзывы"
        override val en = "Maps & reviews"
    }

    object DefaultLeadSourceMapsAndReviewsDescription : LocalizationKey {
        override val ru = "Карточка организации на картах и в сервисах отзывов"
        override val en = "Business listing on maps and review services"
    }

    object DefaultLeadSourceWebsite : LocalizationKey {
        override val ru = "Сайт"
        override val en = "Website"
    }

    object DefaultLeadSourceWebsiteDescription : LocalizationKey {
        override val ru = "Пришёл напрямую через сайт организации"
        override val en = "Came directly through the organization's website"
    }

    object DefaultLeadSourceWalkIn : LocalizationKey {
        override val ru = "Вывеска / проходил мимо"
        override val en = "Walk-in / signage"
    }

    object DefaultLeadSourceWalkInDescription : LocalizationKey {
        override val ru = "Увидел зал, вывеску или листовку поблизости"
        override val en = "Saw the venue, a sign or a flyer nearby"
    }

    object DefaultLeadSourceEvent : LocalizationKey {
        override val ru = "Мероприятие"
        override val en = "Event"
    }

    object DefaultLeadSourceEventDescription : LocalizationKey {
        override val ru = "Соревнования, открытая тренировка, день открытых дверей, выступление"
        override val en = "Competition, open class, open day or performance"
    }

    object DefaultLeadSourceSchool : LocalizationKey {
        override val ru = "Школа / детский сад"
        override val en = "School / kindergarten"
    }

    object DefaultLeadSourceSchoolDescription : LocalizationKey {
        override val ru = "Презентации и объявления в школах и детских садах"
        override val en = "Presentations and announcements at schools and kindergartens"
    }

    object DefaultLeadSourceOther : LocalizationKey {
        override val ru = "Другое"
        override val en = "Other"
    }

    object DefaultLeadSourceOtherDescription : LocalizationKey {
        override val ru = "Всё, что не подходит под остальные источники"
        override val en = "Anything that does not fit the other sources"
    }

    object GroupScheduleChangedTitle : LocalizationTemplate1<String> {
        override val ru = { group: String -> "Изменено расписание группы «$group»" }
        override val en = { group: String -> "Schedule changed for group “$group”" }
    }

    object GroupScheduleChangedBody : LocalizationTemplate2<LocalDate, String> {
        override val ru = { from: LocalDate, author: String ->
            "Новое расписание действует с ${from.localized(Lang.RU)}. Автор изменения: $author"
        }
        override val en = { from: LocalDate, author: String ->
            "The new schedule takes effect on ${from.localized(Lang.EN)}. Changed by $author"
        }
    }
}
