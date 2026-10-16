package org.athletica.infra.messaging.telegram

import kotlinx.serialization.json.Json
import org.athletica.crm.core.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты сведения обновлений Telegram к [InboundEvent] на фикстурах JSON. */
class InboundEventTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun update(message: String): TgUpdate = json.decodeFromString(TgUpdate.serializer(), """{"update_id":1,"message":$message}""")

    private fun privateMessage(fields: String): TgUpdate =
        update(
            """{"message_id":10,"date":0,"from":{"id":5,"is_bot":false,"first_name":"Пётр","last_name":"Иванов","username":"petr"},
               "chat":{"id":5,"type":"private"},$fields}""",
        )

    private val sender = TelegramSender("5", "Пётр Иванов", "petr")

    @Test
    fun `команда start`() {
        assertEquals(InboundEvent.Start(sender), privateMessage(""""text":"/start"""").toInboundEvent(Lang.RU))
        assertEquals(InboundEvent.Start(sender), privateMessage(""""text":"/start site"""").toInboundEvent(Lang.RU))
    }

    @Test
    fun `текст`() {
        assertEquals(
            InboundEvent.Message(sender, "Добрый день, можно перенести занятие?"),
            privateMessage(""""text":"Добрый день, можно перенести занятие?"""").toInboundEvent(Lang.RU),
        )
    }

    @Test
    fun `свой и чужой контакт`() {
        assertEquals(
            InboundEvent.OwnContact(sender, "+79001234567"),
            privateMessage(""""contact":{"phone_number":"+79001234567","first_name":"Пётр","user_id":5}""").toInboundEvent(Lang.RU),
        )
        assertEquals(
            InboundEvent.ForeignContact(sender),
            privateMessage(""""contact":{"phone_number":"+79990000000","first_name":"Мама","user_id":77}""").toInboundEvent(Lang.RU),
        )
        assertEquals(
            InboundEvent.ForeignContact(sender),
            privateMessage(""""contact":{"phone_number":"+79990000000","first_name":"Без Telegram"}""").toInboundEvent(Lang.RU),
        )
    }

    @Test
    fun `вложения с подписью и без на языке организации`() {
        assertEquals(
            InboundEvent.Message(sender, "[вложение: фото] справка"),
            privateMessage(""""photo":[{"file_id":"a","width":1,"height":1}],"caption":"справка"""").toInboundEvent(Lang.RU),
        )
        assertEquals(
            InboundEvent.Message(sender, "[attachment: voice message]"),
            privateMessage(""""voice":{"file_id":"v","duration":3}""").toInboundEvent(Lang.EN),
        )
        assertEquals(InboundEvent.Message(sender, "[вложение: стикер]"), privateMessage(""""sticker":{"file_id":"s"}""").toInboundEvent(Lang.RU))
        assertEquals(InboundEvent.Message(sender, "[вложение: прочее]"), privateMessage(""""poll":{"id":"p"}""").toInboundEvent(Lang.RU))
    }

    @Test
    fun `сообщения групп и не-сообщения игнорируются`() {
        val group =
            update("""{"message_id":1,"date":0,"from":{"id":5,"first_name":"Пётр"},"chat":{"id":-100,"type":"group"},"text":"всем привет"}""")

        assertEquals(InboundEvent.Ignored, group.toInboundEvent(Lang.RU))
        assertEquals(InboundEvent.Ignored, TgUpdate(updateId = 2).toInboundEvent(Lang.RU))
    }
}
