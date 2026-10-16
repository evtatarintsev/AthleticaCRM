package org.athletica.infra.messaging.telegram

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.ChannelChat
import org.athletica.crm.i18n.BotMessages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.usecases.messaging.InboundStores
import org.athletica.crm.usecases.messaging.PhoneLinkResult
import org.athletica.crm.usecases.messaging.linkChatByPhone
import org.athletica.crm.usecases.messaging.receiveInbound
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Включённая Telegram-интеграция, которую нужно опрашивать. */
data class PolledIntegration(
    /** Идентификатор интеграции. */
    val id: ChannelIntegrationId,
    /** Организация интеграции — в её системном контексте обрабатываются входящие. */
    val orgId: OrgId,
    /** Токен бота. */
    val token: String,
) {
    /** Числовой id бота — часть токена до `:`. */
    val botId: String get() = token.substringBefore(':')

    override fun toString(): String = "PolledIntegration(id=$id, orgId=$orgId, botId=$botId)"
}

/** Фоновая задача опроса одной интеграции; [run] работает, пока корутину не отменят. */
fun interface PollingJob {
    /** Запускает бесконечный цикл опроса. */
    suspend fun run()
}

/** Ответ бота, который отправляется после коммита транзакции обработки. */
data class BotReply(
    /** Адрес чата. */
    val chatId: String,
    /** Текст ответа. */
    val text: String,
    /** Клавиатура ответа; `null` — без изменений. */
    val markup: TgReplyMarkup?,
)

/**
 * Поллер одной Telegram-интеграции [integration]. При старте снимает вебхук, затем в цикле:
 * захватывает аренду, получает пачку обновлений `getUpdates` вне транзакции и в одной транзакции
 * проверяет аренду, записывает входящие, чаты, связки и сдвигает offset. Откат транзакции не сдвигает
 * offset, и Telegram отдаст те же обновления снова. Служебные ответы бота отправляются после коммита —
 * для них допустимо «не более одного раза».
 */
class TelegramPoller(
    private val integration: PolledIntegration,
    private val api: TelegramBotApi,
    private val db: Database,
    private val state: TelegramPollState,
    private val stores: InboundStores,
    /** Идентификатор экземпляра сервера — владелец аренды. */
    private val owner: String,
    private val pollTimeoutSeconds: Int = 25,
    private val retryDelay: Duration = 5.seconds,
) : PollingJob {
    private val logger = LoggerFactory.getLogger(TelegramPoller::class.java)

    override suspend fun run() {
        logger.info("Опрос Telegram запущен: {}", integration)
        val webhook = api.deleteWebhook(integration.token)
        if (webhook !is TelegramResult.Ok) {
            logger.warn("Не удалось снять вебхук {}: {}", integration, webhook)
        }
        while (currentCoroutineContext().isActive) {
            val polled =
                try {
                    pollOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Ошибка опроса Telegram {}", integration, e)
                    false
                }
            if (!polled) {
                delay(retryDelay)
            }
        }
    }

    /**
     * Один цикл опроса. `true` — опрос прошёл (возможно, без обновлений); `false` — аренда у другого
     * экземпляра, Bot API недоступен или обработка откатилась: стоит подождать перед повтором.
     */
    suspend fun pollOnce(): Boolean {
        val offset = db.transaction { state.acquire(integration.id, owner, integration.botId) } ?: return false
        val updates =
            when (val result = api.getUpdates(integration.token, offset, pollTimeoutSeconds, BATCH_LIMIT)) {
                is TelegramResult.Ok -> result.value
                is TelegramResult.Error -> {
                    logger.warn("getUpdates {}: {} {}", integration, result.status, result.description)
                    return false
                }
                TelegramResult.Unavailable -> return false
            }
        if (updates.isEmpty()) {
            return true
        }
        val replies =
            either { db.transaction { processed(updates) } }
                .getOrElse { error ->
                    logger.warn("Обработка пачки Telegram {} откатилась: {}", integration, error.code)
                    return false
                }
                ?: return false
        replies.forEach { reply ->
            val sent = api.sendMessage(integration.token, reply.chatId, reply.text, reply.markup)
            if (sent !is TelegramResult.Ok) {
                logger.warn("Служебный ответ бота {} не отправлен: {}", integration, sent)
            }
        }
        return true
    }

    /**
     * Обрабатывает пачку [updates] в транзакции записи и сдвигает offset. `null` — аренду перехватили,
     * ничего не записано. Возвращает служебные ответы бота.
     */
    context(tr: Transaction, raise: Raise<DomainError>)
    private suspend fun processed(updates: List<TgUpdate>): List<BotReply>? {
        if (!state.holds(integration.id, owner)) {
            return null
        }
        val ctx = SystemContexts.forOrg(integration.orgId)
        val replies = context(ctx) { updates.flatMap { replies(it.toInboundEvent(ctx.lang)) } }
        state.advance(integration.id, owner, updates.maxOf { it.updateId } + 1)
        return replies
    }

    /** Записывает событие [event] и возвращает служебные ответы на него. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun replies(event: InboundEvent): List<BotReply> =
        when (event) {
            InboundEvent.Ignored -> emptyList()

            is InboundEvent.Start -> welcomeIfNeeded(chat(event.sender))

            is InboundEvent.OwnContact -> {
                chat(event.sender, event.phone)
                when (linkChatByPhone(integration.id, event.sender.address, event.phone, stores)) {
                    is PhoneLinkResult.Linked -> listOf(reply(event.sender, BotMessages.Linked.localize(), TgReplyMarkup.Remove))
                    PhoneLinkResult.NotMatched -> listOf(reply(event.sender, BotMessages.PhoneReceived.localize(), TgReplyMarkup.Remove))
                    PhoneLinkResult.AlreadyLinked -> emptyList()
                }
            }

            is InboundEvent.ForeignContact -> {
                val chat = chat(event.sender)
                if (needsPhone(chat)) {
                    listOf(reply(event.sender, BotMessages.ForeignContact.localize(), shareContact()))
                } else {
                    emptyList()
                }
            }

            is InboundEvent.Message -> {
                val chat = chat(event.sender)
                val clientId = receiveInbound(integration.id, event.sender.address, event.body, stores)
                if (clientId == null) welcomeIfNeeded(chat) else emptyList()
            }
        }

    /** Создаёт или обновляет чат собеседника [sender]; [phone] — его собственный номер, если прислан. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun chat(sender: TelegramSender, phone: String? = null): ChannelChat = stores.chats.upsert(integration.id, sender.address, sender.displayName, sender.username, phone)

    /** Приветствие с кнопкой «Поделиться номером», пока у чата нет ни номера, ни связки. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun welcomeIfNeeded(chat: ChannelChat): List<BotReply> =
        if (needsPhone(chat)) {
            listOf(BotReply(chat.address, BotMessages.Welcome.localize(), shareContact()))
        } else {
            emptyList()
        }

    /** Нужно ли просить номер: чат его не присылал и не привязан к клиенту. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun needsPhone(chat: ChannelChat): Boolean = chat.phone == null && stores.bindings.clientFor(integration.id, chat.address) == null

    /** Клавиатура с кнопкой запроса контакта. */
    context(ctx: RequestContext)
    private fun shareContact(): TgReplyMarkup = TgReplyMarkup.RequestContact(BotMessages.ShareContactButton.localize())

    /** Ответ собеседнику [sender]. */
    private fun reply(sender: TelegramSender, text: String, markup: TgReplyMarkup?): BotReply = BotReply(sender.address, text, markup)

    private companion object {
        /** Максимум обновлений в одной пачке: обработка пачки должна укладываться в срок аренды. */
        const val BATCH_LIMIT = 50
    }
}
