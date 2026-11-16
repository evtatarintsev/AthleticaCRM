package org.athletica.crm.domain.clientcard

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Включаемые разделы карточки; выключение не удаляет данные. */
interface ClientCardSections {
    val tasks: Boolean
    val notes: Boolean
    val chat: Boolean
    val documents: Boolean

    fun withNew(
        tasks: Boolean,
        notes: Boolean,
        chat: Boolean,
        documents: Boolean,
    ): ClientCardSections

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save()
}

/** Настройки карточки организации из контекста. */
interface ClientCardSectionSettings {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun current(): ClientCardSections
}
