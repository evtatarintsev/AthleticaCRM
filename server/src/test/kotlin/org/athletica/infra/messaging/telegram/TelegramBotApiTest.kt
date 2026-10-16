package org.athletica.infra.messaging.telegram

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты клиента Bot API [TelegramBotApi]: перебор прокси и разбор ответов. */
class TelegramBotApiTest {
    @Test
    fun `отказ первого прокси — запрос через следующий, и он становится основным`() =
        runTest {
            var firstCalls = 0
            var secondCalls = 0
            val api =
                TelegramBotApi(
                    listOf(
                        mockTelegramClient {
                            firstCalls++
                            throw IOException("connection refused")
                        },
                        mockTelegramClient {
                            secondCalls++
                            respondJson("""{"ok":true,"result":{"id":42,"is_bot":true,"first_name":"Bot","username":"club_bot"}}""")
                        },
                    ),
                )

            val first = assertIs<TelegramResult.Ok<TgUser>>(api.getMe("1:t")).value
            api.getMe("1:t")

            assertEquals("club_bot", first.username)
            assertEquals(1, firstCalls)
            assertEquals(2, secondCalls)
        }

    @Test
    fun `ответ ok=false не запускает перебор`() =
        runTest {
            var secondCalls = 0
            val api =
                TelegramBotApi(
                    listOf(
                        mockTelegramClient {
                            respondJson(
                                """{"ok":false,"error_code":401,"description":"Unauthorized"}""",
                                io.ktor.http.HttpStatusCode.Unauthorized,
                            )
                        },
                        mockTelegramClient {
                            secondCalls++
                            respondJson("""{"ok":true,"result":true}""")
                        },
                    ),
                )

            val result = api.getMe("1:bad")

            assertEquals(TelegramResult.Error(401, "Unauthorized"), result)
            assertEquals(0, secondCalls)
        }

    @Test
    fun `все прокси недоступны`() =
        runTest {
            val api = TelegramBotApi(listOf(mockTelegramClient { throw IOException("down") }, mockTelegramClient { throw IOException("down") }))

            assertEquals(TelegramResult.Unavailable, api.sendMessage("1:t", "100", "текст"))
        }

    @Test
    fun `разбор обновлений и отправка с клавиатурой запроса контакта`() =
        runTest {
            var sentBody = ""
            val api =
                telegramTestApi { request ->
                    when (request.telegramMethod) {
                        "getUpdates" ->
                            respondJson(
                                """
                                {"ok":true,"result":[
                                  {"update_id":7,"message":{"message_id":1,"date":0,"from":{"id":5,"is_bot":false,"first_name":"Пётр"},
                                   "chat":{"id":5,"type":"private"},"text":"Привет","entities":[]}},
                                  {"update_id":8,"edited_message":{"message_id":1}}
                                ]}
                                """.trimIndent(),
                            )
                        else -> {
                            sentBody = (request.body as io.ktor.http.content.TextContent).text
                            respondJson("""{"ok":true,"result":{"message_id":99,"date":0,"chat":{"id":5,"type":"private"}}}""")
                        }
                    }
                }

            val updates = assertIs<TelegramResult.Ok<List<TgUpdate>>>(api.getUpdates("1:t", 7, 0, 50)).value
            val sent = assertIs<TelegramResult.Ok<TgMessage>>(api.sendMessage("1:t", "5", "Здравствуйте", TgReplyMarkup.RequestContact("Поделиться"))).value

            assertEquals(listOf(7L, 8L), updates.map { it.updateId })
            assertEquals("Привет", updates.first().message?.text)
            assertEquals(null, updates.last().message)
            assertEquals(99L, sent.messageId)
            assertEquals(true, sentBody.contains("\"request_contact\":true"))
        }
}
