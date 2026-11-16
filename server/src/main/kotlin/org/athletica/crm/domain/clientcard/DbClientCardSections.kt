package org.athletica.crm.domain.clientcard

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean

/** Разделы карточки, сохранённые в PostgreSQL. */
data class DbClientCardSections(
    override val tasks: Boolean = false,
    override val notes: Boolean = false,
    override val chat: Boolean = false,
    override val documents: Boolean = false,
) : ClientCardSections {
    override fun withNew(
        tasks: Boolean,
        notes: Boolean,
        chat: Boolean,
        documents: Boolean,
    ): ClientCardSections = copy(tasks = tasks, notes = notes, chat = chat, documents = documents)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        tr.sql(
            """
            INSERT INTO client_card_sections (org_id, tasks, notes, chat, documents)
            VALUES (:orgId, :tasks, :notes, :chat, :documents)
            ON CONFLICT (org_id) DO UPDATE
            SET tasks = EXCLUDED.tasks, notes = EXCLUDED.notes,
                chat = EXCLUDED.chat, documents = EXCLUDED.documents
            """.trimIndent(),
        ).bind("orgId", ctx.orgId).bind("tasks", tasks).bind("notes", notes)
            .bind("chat", chat).bind("documents", documents).execute()
    }
}

/** Без сохранённых настроек все включаемые разделы скрыты. */
class DbClientCardSectionSettings : ClientCardSectionSettings {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun current(): ClientCardSections =
        tr.sql("SELECT tasks, notes, chat, documents FROM client_card_sections WHERE org_id = :orgId")
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                DbClientCardSections(row.asBoolean("tasks"), row.asBoolean("notes"), row.asBoolean("chat"), row.asBoolean("documents"))
            } ?: DbClientCardSections()
}
