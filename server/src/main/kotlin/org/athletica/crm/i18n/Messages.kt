package org.athletica.crm.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.audit.AuditActionType

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
    object RegistrationEmailTitle : LocalizationKey {
        override val ru = "Добро пожаловать в AthleticaCRM"
        override val en = "Welcome to AthleticaCRM"
    }

    object EmailGreeting : LocalizationTemplate1<String> {
        override val ru = { name: String -> "Здравствуйте, $name!" }
        override val en = { name: String -> "Hello, $name!" }
    }

    object RegistrationEmailBody : LocalizationKey {
        override val ru = "Спасибо за регистрацию! Подтвердите ваш email. Вы уже можете работать в CRM без ограничений."
        override val en = "Thank you for signing up! Please confirm your email. You can already use the CRM without restrictions."
    }

    object EmailConfirmationExpiry : LocalizationKey {
        override val ru = "Ссылка действует 24 часа. Если кнопка не работает, откройте ссылку ниже."
        override val en = "The link expires in 24 hours. If the button does not work, open the link below."
    }

    object ConfirmEmailAction : LocalizationKey {
        override val ru = "Подтвердить email"
        override val en = "Confirm email"
    }

    object RegistrationEmailFooter : LocalizationKey {
        override val ru = "Вы получили письмо, потому что этот адрес указан при регистрации в AthleticaCRM. Если это были не вы, просто проигнорируйте письмо."
        override val en = "You received this email because this address was used to sign up for AthleticaCRM. If this was not you, simply ignore this email."
    }

    object EmailConfirmedTitle : LocalizationKey {
        override val ru = "Email подтверждён"
        override val en = "Email confirmed"
    }

    object EmailConfirmedBody : LocalizationTemplate1<String> {
        override val ru = { email: String -> "Адрес $email успешно подтверждён." }
        override val en = { email: String -> "The address $email has been confirmed." }
    }

    object InvalidEmailConfirmation : LocalizationKey {
        override val ru = "Ссылка недействительна или срок её действия истёк. Войдите в CRM и запросите новое письмо."
        override val en = "The link is invalid or expired. Sign in to the CRM and request a new email."
    }

    object InvalidRegistrationEmail : LocalizationKey {
        override val ru = "Укажите корректный email"
        override val en = "Enter a valid email"
    }

    object EmailVerificationUnavailable : LocalizationKey {
        override val ru = "Подтверждение email для этого аккаунта не запрашивалось"
        override val en = "Email confirmation was not requested for this account"
    }

    object PasswordResetEmailTitle : LocalizationKey {
        override val ru = "Сброс пароля AthleticaCRM"
        override val en = "AthleticaCRM password reset"
    }

    object PasswordResetEmailBody : LocalizationKey {
        override val ru = "Мы получили запрос на сброс пароля вашего аккаунта. Чтобы задать новый пароль, нажмите кнопку ниже. После сброса все открытые сеансы будут завершены."
        override val en = "We received a request to reset the password for your account. To set a new password, press the button below. All open sessions will be signed out after the reset."
    }

    object PasswordResetExpiry : LocalizationKey {
        override val ru = "Ссылка действует 1 час и сработает один раз. Если кнопка не работает, откройте ссылку ниже."
        override val en = "The link expires in 1 hour and works once. If the button does not work, open the link below."
    }

    object PasswordResetAction : LocalizationKey {
        override val ru = "Задать новый пароль"
        override val en = "Set a new password"
    }

    object PasswordResetEmailFooter : LocalizationKey {
        override val ru = "Вы получили письмо, потому что для этого адреса запросили сброс пароля в AthleticaCRM. Если это были не вы, просто проигнорируйте письмо — пароль не изменится."
        override val en = "You received this email because a password reset was requested for this address in AthleticaCRM. If this was not you, simply ignore this email: your password will not change."
    }

    object InvalidPasswordReset : LocalizationKey {
        override val ru = "Ссылка недействительна или срок её действия истёк. Запросите новое письмо для сброса пароля."
        override val en = "The link is invalid or expired. Request a new password reset email."
    }

    object EmptyNewPassword : LocalizationKey {
        override val ru = "Введите новый пароль"
        override val en = "Enter a new password"
    }

    /** Короткое название типа действия для описания события журнала. */
    fun auditAction(action: AuditActionType, lang: Lang): String =
        when (lang) {
            Lang.RU ->
                when (action) {
                    AuditActionType.CREATE -> "создание"
                    AuditActionType.UPDATE -> "изменение"
                    AuditActionType.DELETE -> "удаление"
                    AuditActionType.AUTH_LOGIN -> "вход в систему"
                    AuditActionType.AUTH_LOGOUT -> "выход из системы"
                    AuditActionType.AUTH_SIGNUP -> "регистрация"
                    AuditActionType.AUTH_VERIFY_EMAIL -> "подтверждение email"
                    AuditActionType.MESSAGE_SEND -> "отправка сообщения"
                    AuditActionType.EXPORT -> "экспорт данных"
                    AuditActionType.IMPORT -> "импорт данных"
                    AuditActionType.AUTH_CHANGE_PASSWORD -> "смена пароля"
                    AuditActionType.AUTH_PASSWORD_RESET -> "сброс пароля"
                    AuditActionType.BALANCE_ADJUST -> "корректировка баланса"
                }
            Lang.EN ->
                when (action) {
                    AuditActionType.CREATE -> "created"
                    AuditActionType.UPDATE -> "updated"
                    AuditActionType.DELETE -> "deleted"
                    AuditActionType.AUTH_LOGIN -> "signed in"
                    AuditActionType.AUTH_LOGOUT -> "signed out"
                    AuditActionType.AUTH_SIGNUP -> "signed up"
                    AuditActionType.AUTH_VERIFY_EMAIL -> "confirmed email"
                    AuditActionType.MESSAGE_SEND -> "sent a message"
                    AuditActionType.EXPORT -> "exported data"
                    AuditActionType.IMPORT -> "imported data"
                    AuditActionType.AUTH_CHANGE_PASSWORD -> "changed password"
                    AuditActionType.AUTH_PASSWORD_RESET -> "reset password"
                    AuditActionType.BALANCE_ADJUST -> "adjusted balance for"
                }
        }

    /** Название типа субъекта в подходящей форме для строки журнала. */
    fun auditEntity(entityType: String?, lang: Lang): String =
        when (lang) {
            Lang.RU ->
                when (entityType) {
                    "client" -> "клиента"
                    "client_doc" -> "документ клиента"
                    "group" -> "группы"
                    "employee" -> "сотрудника"
                    "session" -> "занятия"
                    "branch" -> "филиала"
                    "hall" -> "зала"
                    "discipline" -> "дисциплины"
                    "lead_source" -> "источника лида"
                    "custom_field" -> "дополнительного поля"
                    "subscription_tariff" -> "тарифа"
                    "channel_integration" -> "канала связи"
                    "enrollment" -> "участия клиента в группе"
                    "membership" -> "абонемента"
                    "upload" -> "файла"
                    else -> "объекта"
                }
            Lang.EN ->
                when (entityType) {
                    "client" -> "client"
                    "client_doc" -> "client document"
                    "group" -> "group"
                    "employee" -> "employee"
                    "session" -> "session"
                    "branch" -> "branch"
                    "hall" -> "hall"
                    "discipline" -> "discipline"
                    "lead_source" -> "lead source"
                    "custom_field" -> "custom field"
                    "subscription_tariff" -> "subscription tariff"
                    "channel_integration" -> "channel integration"
                    "enrollment" -> "client enrollment"
                    "membership" -> "membership"
                    "upload" -> "file"
                    else -> "object"
                }
        }

    /** Краткое описание действия в журнале с именем автора и деталей события. */
    object AuditLogEntry : LocalizationTemplate2<String, String> {
        override val ru = { username: String, details: String -> "$username: $details" }
        override val en = { username: String, details: String -> "$username: $details" }
    }

    /** Отображаемое имя субъекта, который уже нельзя разрешить из текущего состояния данных. */
    object AuditSubjectUnavailable : LocalizationKey {
        override val ru = "Объект больше недоступен"
        override val en = "Object is no longer available"
    }

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

    object ChatNotFound : LocalizationKey {
        override val ru = "Чат не найден"
        override val en = "Chat not found"
    }

    object InvalidTimestamp : LocalizationKey {
        override val ru = "Неверный формат момента времени"
        override val en = "Invalid timestamp format"
    }

    object ChatAlreadyLinked : LocalizationKey {
        override val ru = "Чат уже привязан к клиенту"
        override val en = "This chat is already linked to a client"
    }

    object TelegramTokenInvalid : LocalizationKey {
        override val ru = "Telegram не принял токен бота — проверьте его в BotFather"
        override val en = "Telegram rejected the bot token — check it in BotFather"
    }

    object TelegramGatewayTokenInvalid : LocalizationKey {
        override val ru = "Telegram Gateway не принял токен — проверьте его в личном кабинете Gateway"
        override val en = "Telegram Gateway rejected the token — check it in your Gateway account"
    }

    object TelegramGatewayUnavailable : LocalizationKey {
        override val ru = "Telegram Gateway сейчас недоступен — попробуйте позже"
        override val en = "Telegram Gateway is unavailable right now — try again later"
    }

    object TelegramGatewayNotConnected : LocalizationKey {
        override val ru = "Telegram Gateway не подключён"
        override val en = "Telegram Gateway is not connected"
    }

    object TelegramUnavailable : LocalizationKey {
        override val ru = "Telegram сейчас недоступен — попробуйте сохранить позже"
        override val en = "Telegram is unavailable right now — try saving later"
    }

    object TelegramBotInUse : LocalizationKey {
        override val ru = "Этот бот уже подключён к другому каналу"
        override val en = "This bot is already connected to another channel"
    }

    object SmsAeroFieldsRequired : LocalizationKey {
        override val ru = "Укажите email, API-ключ и подпись SMS Aero"
        override val en = "Enter the SMS Aero email, API key and sender name"
    }

    object SmsAeroAuthInvalid : LocalizationKey {
        override val ru = "Неверный email или API-ключ SMS Aero"
        override val en = "Invalid SMS Aero email or API key"
    }

    object SmsAeroSignNotFound : LocalizationTemplate1<String> {
        override val ru = { sign: String -> "Подписи $sign нет в аккаунте SMS Aero — добавьте её в кабинете SMS Aero" }
        override val en = { sign: String -> "Sender name $sign is not in the SMS Aero account — add it in the SMS Aero dashboard" }
    }

    object SmsAeroSignNotApproved : LocalizationTemplate1<String> {
        override val ru = { sign: String -> "Подпись $sign ещё не одобрена в SMS Aero" }
        override val en = { sign: String -> "Sender name $sign is not approved in SMS Aero yet" }
    }

    object SmsAeroUnavailable : LocalizationKey {
        override val ru = "SMS Aero сейчас недоступен — попробуйте позже"
        override val en = "SMS Aero is unavailable right now — try again later"
    }

    object SmsAeroRejected : LocalizationTemplate1<String> {
        override val ru = { reason: String -> "SMS Aero отклонил сообщение: $reason" }
        override val en = { reason: String -> "SMS Aero rejected the message: $reason" }
    }

    object SmsAeroNoResponse : LocalizationKey {
        override val ru = "SMS Aero не ответил вовремя — сообщение могло уйти, проверьте его в кабинете SMS Aero"
        override val en = "SMS Aero did not respond in time — the message may have been sent, check it in the SMS Aero dashboard"
    }

    object SmsAeroNoFunds : LocalizationKey {
        override val ru = "Недостаточно средств на балансе SMS Aero"
        override val en = "Insufficient funds on the SMS Aero balance"
    }

    object SmsAeroIpForbidden : LocalizationKey {
        override val ru = "IP-адрес сервера не в белом списке SMS Aero — проверьте настройки в кабинете SMS Aero"
        override val en = "The server IP address is not in the SMS Aero allowlist — check the SMS Aero dashboard settings"
    }

    object ChannelProviderMismatch : LocalizationKey {
        override val ru = "Провайдера канала изменить нельзя"
        override val en = "The channel provider cannot be changed"
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

    object EmployeeAccessAlreadySent : LocalizationKey {
        override val ru = "Доступ сотруднику уже выдан"
        override val en = "Employee access has already been granted"
    }

    object EmployeeIsBlocked : LocalizationKey {
        override val ru = "Сотрудник заблокирован: сначала снимите блокировку"
        override val en = "The employee is blocked: unblock them first"
    }

    object EmployeeOwnerCannotBeBlocked : LocalizationKey {
        override val ru = "Владельца организации нельзя заблокировать"
        override val en = "The organization owner cannot be blocked"
    }

    object EmployeeCannotBlockSelf : LocalizationKey {
        override val ru = "Нельзя заблокировать самого себя"
        override val en = "You cannot block yourself"
    }

    object AuthSessionNotFound : LocalizationKey {
        override val ru = "Сеанс не найден"
        override val en = "Session not found"
    }

    object CannotTerminateCurrentSession : LocalizationKey {
        override val ru = "Текущий сеанс завершается выходом"
        override val en = "Use sign out to end the current session"
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

    object InvalidClientSort : LocalizationKey {
        override val ru = "Некорректная сортировка: поля не должны повторяться, а сортировка по ближайшему дню рождения требует фильтр по дню рождения"
        override val en = "Invalid sort: fields must not repeat, and sorting by next birthday requires a birthday filter"
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

    object WidgetNotFound : LocalizationKey {
        override val ru = "Виджет не найден"
        override val en = "Widget not found"
    }

    object WidgetUnavailable : LocalizationKey {
        override val ru = "Расписание недоступно"
        override val en = "Schedule is unavailable"
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

    object MembershipNotFound : LocalizationKey {
        override val ru = "Абонемент не найден"
        override val en = "Membership not found"
    }

    /** Позиция продажи для абонемента не найдена, отменена или принадлежит другому клиенту. */
    object MembershipSaleItemNotFound : LocalizationKey {
        override val ru = "Позиция продажи абонемента не найдена"
        override val en = "Membership sale item not found"
    }

    object MembershipUnlimitedCloseOnLastSession : LocalizationKey {
        override val ru = "Безлимитный абонемент нельзя закрывать при списании последнего занятия"
        override val en = "An unlimited membership cannot close on its last session"
    }

    object MembershipDeadlineBeforeIssue : LocalizationKey {
        override val ru = "Крайняя дата активации не может быть раньше даты выдачи"
        override val en = "The activation deadline cannot be earlier than the issue date"
    }

    object MembershipActivationDateRequired : LocalizationKey {
        override val ru = "Укажите дату активации"
        override val en = "Specify the activation date"
    }

    object MembershipNotActive : LocalizationKey {
        override val ru = "Действие доступно только для активного абонемента"
        override val en = "This action is available only for an active membership"
    }

    object MembershipFinal : LocalizationKey {
        override val ru = "Абонемент закрыт или отозван, действие недоступно"
        override val en = "The membership is closed or revoked; the action is unavailable"
    }

    object MembershipChangedConcurrently : LocalizationKey {
        override val ru = "Абонемент изменён параллельно, повторите действие"
        override val en = "The membership was changed concurrently; please retry"
    }

    object MembershipClosePendingTitle : LocalizationTemplate1<String> {
        override val ru = { membership: String -> "Абонемент «$membership» можно закрыть" }
        override val en = { membership: String -> "Membership “$membership” can be closed" }
    }

    object MembershipClosePendingBody : LocalizationKey {
        override val ru = "Вышел срок или закончились занятия. Закройте абонемент в карточке клиента."
        override val en = "The term has ended or no sessions are left. Close the membership on the client card."
    }

    object MembershipGroupsRequired : LocalizationKey {
        override val ru = "Выберите хотя бы одну группу, в которой действует абонемент"
        override val en = "Select at least one group the membership is valid in"
    }

    object MembershipGroupNotFound : LocalizationKey {
        override val ru = "Группа абонемента не найдена"
        override val en = "Membership group not found"
    }

    object MembershipHasDebits : LocalizationKey {
        override val ru = "С абонемента списаны занятия: сначала верните их, затем отзывайте абонемент"
        override val en = "Sessions were debited from this membership: return them before revoking it"
    }

    object SessionNotFound : LocalizationKey {
        override val ru = "Занятие не найдено"
        override val en = "Session not found"
    }

    object MembershipDebtExceedsRemaining : LocalizationKey {
        override val ru = "Выбрано больше занятий, чем осталось на абонементе"
        override val en = "More sessions are selected than the membership has left"
    }

    object MembershipFreezeReasonRequired : LocalizationKey {
        override val ru = "Укажите причину заморозки"
        override val en = "Specify the freeze reason"
    }

    object MembershipFreezePeriodInvalid : LocalizationKey {
        override val ru = "Дата окончания заморозки раньше даты начала"
        override val en = "The freeze end date is before its start date"
    }

    object MembershipFreezeInPast : LocalizationKey {
        override val ru = "Заморозка не может начинаться в прошлом"
        override val en = "A freeze cannot start in the past"
    }

    object MembershipFreezeAfterEnd : LocalizationKey {
        override val ru = "Заморозка не может начинаться после окончания абонемента"
        override val en = "A freeze cannot start after the membership ends"
    }

    object MembershipAlreadyFrozen : LocalizationKey {
        override val ru = "У абонемента уже есть заморозка"
        override val en = "The membership already has a freeze"
    }

    object MembershipNotFrozen : LocalizationKey {
        override val ru = "Абонемент не заморожен"
        override val en = "The membership is not frozen"
    }

    object MembershipExtensionReasonRequired : LocalizationKey {
        override val ru = "Укажите причину продления"
        override val en = "Specify the extension reason"
    }

    object MembershipExtensionNotLater : LocalizationKey {
        override val ru = "Новая дата окончания должна быть позже текущей"
        override val en = "The new end date must be later than the current one"
    }

    object MembershipExtensionFrozen : LocalizationKey {
        override val ru = "Замороженный абонемент нельзя продлить"
        override val en = "A frozen membership cannot be extended"
    }

    object AuditSystemActor : LocalizationKey {
        override val ru = "Система"
        override val en = "System"
    }

    object SessionAlreadyDebited : LocalizationKey {
        override val ru = "Занятие уже списано"
        override val en = "The session has already been debited"
    }

    object SessionNotUnpaid : LocalizationKey {
        override val ru = "Занятие не является неоплаченным занятием клиента в группах абонемента"
        override val en = "The session is not an unpaid session of the client in the membership groups"
    }

    object DebtSessionsRequired : LocalizationKey {
        override val ru = "Выберите занятия для погашения"
        override val en = "Select sessions to repay"
    }

    object CheckClientTaskTitle : LocalizationKey {
        override val ru = "Проверить клиента"
        override val en = "Check the client"
    }

    object CheckClientTaskDescription : LocalizationTemplate2<String, Int> {
        override val ru = { membership: String, count: Int ->
            "Абонемент «$membership» активирован без участия сотрудника. Неоплаченных занятий в группах абонемента: $count. Погасите долг с абонемента или свяжитесь с клиентом."
        }
        override val en = { membership: String, count: Int ->
            "Membership “$membership” was activated automatically. Unpaid sessions in its groups: $count. Repay the debt from the membership or contact the client."
        }
    }

    object OverdueJournalTaskTitle : LocalizationTemplate1<String> {
        override val ru = { group: String -> "Закрыть журнал: $group" }
        override val en = { group: String -> "Close the journal: $group" }
    }

    object OverdueJournalTaskDescription : LocalizationTemplate2<String, String> {
        override val ru = { group: String, time: String ->
            "Занятие группы «$group» $time прошло, но журнал посещаемости не закрыт. Отметьте посещаемость и проведите занятие."
        }
        override val en = { group: String, time: String ->
            "The “$group” session on $time is over, but its attendance journal is not closed. Mark attendance and complete the session."
        }
    }

    /** Наполнение демо-данными отклонено: в организации есть данные сверх созданных регистрацией. */
    object DemoOrgNotEmpty : LocalizationKey {
        override val ru = "Организация уже заполнена (клиенты, группы, дисциплины, тарифы, кассы, поля, филиалы или залы) — наполнить можно только новую организацию"
        override val en = "The organization already has data (clients, groups, disciplines, tariffs, cash accounts, fields, branches or halls) — only a new organization can be filled"
    }

    /** Наполнение демо-данными невозможно: у организации нет владельца. */
    object DemoOwnerNotFound : LocalizationKey {
        override val ru = "У организации нет владельца с учётной записью"
        override val en = "The organization has no owner with an account"
    }

    /** Текст SMS с кодом входа в кабинет: название организации-плательщика и код. */
    object ClientAppSmsCode : LocalizationTemplate2<String, String> {
        override val ru = { org: String, code: String -> "$org: код входа в кабинет $code. Никому его не сообщайте." }
        override val en = { org: String, code: String -> "$org: your sign-in code is $code. Do not share it." }
    }

    object ClientAppTermsRequired : LocalizationKey {
        override val ru = "Примите условия использования, чтобы войти"
        override val en = "Accept the terms of use to sign in"
    }

    object ClientAppCodeWrong : LocalizationKey {
        override val ru = "Неверный код"
        override val en = "Wrong code"
    }

    object ClientAppCodeExpired : LocalizationKey {
        override val ru = "Код больше не действует — запросите новый"
        override val en = "The code is no longer valid — request a new one"
    }

    object ClientAppInvalidPhone : LocalizationKey {
        override val ru = "Укажите номер телефона"
        override val en = "Enter a phone number"
    }

    /** Приглашения организации для номера нет: его уже приняли, отклонили или отозвали. */
    object ClientAppInvitationNotFound : LocalizationKey {
        override val ru = "Приглашение больше не действует"
        override val en = "The invitation is no longer valid"
    }

    /** Заголовок задачи после «Это не я» в кабинете. */
    object ClientAppNotMeTaskTitle : LocalizationKey {
        override val ru = "Проверьте номер телефона"
        override val en = "Check the phone number"
    }

    /** Описание задачи после «Это не я»: номер, владелец которого отказался от приглашения. */
    object ClientAppNotMeTaskDescription : LocalizationTemplate1<String> {
        override val ru = { phone: String ->
            "Владелец номера +$phone сообщил в кабинете, что приглашение не для него. Проверьте номер в карточке клиента и пригласите клиента снова."
        }
        override val en = { phone: String ->
            "The owner of +$phone reported in the client cabinet that the invitation is not for them. Check the number in the client card and invite the client again."
        }
    }

    /** Текст приглашения в кабинет: только название организации и ссылка, без данных клиента. */
    object ClientAppInvitation : LocalizationTemplate2<String, String> {
        override val ru = { org: String, url: String -> "$org приглашает вас в личный кабинет: расписание, абонементы и баланс. Вход по номеру телефона: $url" }
        override val en = { org: String, url: String -> "$org invites you to the client cabinet: schedule, memberships and balance. Sign in with your phone number: $url" }
    }

    /** Кабинет в организации выключен: приглашать нельзя. */
    object ClientAppDisabled : LocalizationKey {
        override val ru = "Клиентский кабинет выключен — включите его в настройках"
        override val en = "The client cabinet is disabled — enable it in the settings"
    }

    /** Включить кабинет нельзя: кодам входа не через что уходить. */
    object ClientAppNoCodeTransport : LocalizationKey {
        override val ru = "Подключите Telegram Gateway или SMS-канал: через них клиенты получают код входа"
        override val en = "Connect Telegram Gateway or an SMS channel: clients receive their sign-in code through them"
    }

    /** Нет права `CAN_MANAGE_CLIENT_APP`. */
    object ClientAppManageDenied : LocalizationKey {
        override val ru = "Недостаточно прав для управления клиентским кабинетом"
        override val en = "You do not have permission to manage the client cabinet"
    }

    /** Номер не найден у клиента или действие для его статуса недоступно. */
    object ClientAppPhoneActionUnavailable : LocalizationKey {
        override val ru = "Действие недоступно для этого номера — обновите страницу"
        override val en = "This action is not available for the number — refresh the page"
    }

    /** «Не придём» недоступно: занятие отменено, проведено или уже началось. */
    object ClientAppAbsenceNoticeClosed : LocalizationKey {
        override val ru = "Предупредить уже нельзя: занятие началось, отменено или проведено"
        override val en = "It is too late to notify: the session has started, was cancelled or completed"
    }
}
