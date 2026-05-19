package org.athletica.crm.ui.list

import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.api.schemas.settings.SortStateSchema

/** Направление сортировки списка. */
enum class SortDirection {
    /** По возрастанию. */
    Asc,

    /** По убыванию. */
    Desc,
}

/**
 * Состояние сортировки списка: одна колонка + направление.
 * Если сортировка снята — представляется как `null`, а не специальным значением.
 */
data class SortState(val columnId: ColumnId, val direction: SortDirection) {
    /** Конвертирует в схему для сохранения в [org.athletica.crm.api.schemas.settings.DisplaySettings]. */
    fun toDto(): SortStateSchema =
        SortStateSchema(
            columnId = columnId.value,
            direction =
                when (direction) {
                    SortDirection.Asc -> SortDirectionSchema.Asc
                    SortDirection.Desc -> SortDirectionSchema.Desc
                },
        )

    companion object {
        /**
         * Циклическое переключение сортировки при клике по заголовку колонки [columnId].
         * Цикл: none → Asc → Desc → none.
         */
        fun cycle(
            current: SortState?,
            columnId: ColumnId,
        ): SortState? =
            when {
                current == null || current.columnId != columnId -> SortState(columnId, SortDirection.Asc)
                current.direction == SortDirection.Asc -> SortState(columnId, SortDirection.Desc)
                else -> null
            }

        /** Восстанавливает из схемы. */
        fun fromDto(dto: SortStateSchema): SortState =
            SortState(
                columnId = ColumnId(dto.columnId),
                direction =
                    when (dto.direction) {
                        SortDirectionSchema.Asc -> SortDirection.Asc
                        SortDirectionSchema.Desc -> SortDirection.Desc
                    },
            )
    }
}
