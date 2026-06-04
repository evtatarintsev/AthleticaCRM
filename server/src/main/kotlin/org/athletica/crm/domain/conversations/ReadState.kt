package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Read horizon диалога: указатель «последнее прочитанное сотрудниками» (инбокс команды).
 * Непрочитанность считается относительно [lastReadAt], а не флагом на каждом сообщении (как у Twilio).
 */
data class ConversationReadState(
    val conversationId: ConversationId,
    val lastReadAt: Instant,
)

/** Репозиторий read horizon диалогов. Изолирован по организации. */
interface ConversationReadStates {
    /** Сдвигает указатель прочитанного диалога [conversationId] на момент [at]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markRead(conversationId: ConversationId, at: Instant)

    /** Возвращает read horizon диалога [conversationId]; `null`, если диалог ещё не читали. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byConversation(conversationId: ConversationId): ConversationReadState?

    /** Число непрочитанных входящих сообщений диалога [conversationId] относительно read horizon. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unreadCount(conversationId: ConversationId): Long
}
