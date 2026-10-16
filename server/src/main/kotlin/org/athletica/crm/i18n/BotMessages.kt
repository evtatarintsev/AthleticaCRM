package org.athletica.crm.i18n

/** Тексты, которые бот связанного канала пишет клиенту, и заглушки вложений во входящих. */
object BotMessages {
    /** Приветствие с просьбой поделиться номером. */
    object Welcome : LocalizationKey {
        override val ru = "Здравствуйте! Чтобы мы узнали вас, поделитесь, пожалуйста, своим номером телефона — кнопка ниже."
        override val en = "Hello! To help us recognise you, please share your phone number using the button below."
    }

    /** Текст кнопки запроса контакта. */
    object ShareContactButton : LocalizationKey {
        override val ru = "Поделиться номером"
        override val en = "Share phone number"
    }

    /** Повторная просьба после пересланного чужого контакта. */
    object ForeignContact : LocalizationKey {
        override val ru = "Это чужой контакт. Поделитесь, пожалуйста, своим номером — кнопка ниже."
        override val en = "This is someone else's contact. Please share your own number using the button below."
    }

    /** Подтверждение автоматической привязки: данные клиента не раскрываются. */
    object Linked : LocalizationKey {
        override val ru = "Спасибо, мы вас узнали! Пишите сюда — вам ответят сотрудники."
        override val en = "Thank you, we've recognised you! Write here and our staff will reply."
    }

    /** Ответ на присланный номер, который не удалось сопоставить автоматически. */
    object PhoneReceived : LocalizationKey {
        override val ru = "Спасибо! Номер получен, сотрудники скоро с вами свяжутся."
        override val en = "Thank you! We've got your number, our staff will contact you soon."
    }

    /** Заглушка вложения во входящем: `[вложение: <вид>]`. */
    object Attachment : LocalizationTemplate1<String> {
        override val ru = { kind: String -> "[вложение: $kind]" }
        override val en = { kind: String -> "[attachment: $kind]" }
    }

    /** Вид вложения: фото. */
    object AttachmentPhoto : LocalizationKey {
        override val ru = "фото"
        override val en = "photo"
    }

    /** Вид вложения: видео. */
    object AttachmentVideo : LocalizationKey {
        override val ru = "видео"
        override val en = "video"
    }

    /** Вид вложения: голосовое сообщение. */
    object AttachmentVoice : LocalizationKey {
        override val ru = "голосовое"
        override val en = "voice message"
    }

    /** Вид вложения: аудио. */
    object AttachmentAudio : LocalizationKey {
        override val ru = "аудио"
        override val en = "audio"
    }

    /** Вид вложения: документ. */
    object AttachmentDocument : LocalizationKey {
        override val ru = "документ"
        override val en = "document"
    }

    /** Вид вложения: стикер. */
    object AttachmentSticker : LocalizationKey {
        override val ru = "стикер"
        override val en = "sticker"
    }

    /** Вид вложения: геопозиция. */
    object AttachmentLocation : LocalizationKey {
        override val ru = "геопозиция"
        override val en = "location"
    }

    /** Вид вложения: прочее. */
    object AttachmentOther : LocalizationKey {
        override val ru = "прочее"
        override val en = "other"
    }
}
